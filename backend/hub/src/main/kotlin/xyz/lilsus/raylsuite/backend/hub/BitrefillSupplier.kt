package xyz.lilsus.raylsuite.backend.hub

import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.PaymentRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Currency
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import xyz.lilsus.raylsuite.core.hubapi.HubServiceAmountRange
import xyz.lilsus.raylsuite.core.hubapi.HubServiceContent
import xyz.lilsus.raylsuite.core.hubapi.HubServiceLightningPayment
import xyz.lilsus.raylsuite.core.hubapi.HubServiceMoney
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOffer
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrderRequest

/** Personal API experiment: only unpaid Lightning invoices, never account-balance spending. */
class BitrefillSupplier(
    private val apiKey: String,
    private val client: HttpClient,
    private val topupProductId: String = "claro-el-salvador",
    private val packagesProductId: String = "claro-el-salvador-bundles",
    private val country: String = "SV",
    private val callingCode: String = "503",
    private val clock: Clock = Clock.systemUTC(),
    private val baseUrl: String = "https://api.bitrefill.com/v2"
) : ServiceSupplier {
    override val id = "bitrefill"
    private val json = Json { ignoreUnknownKeys = true }
    private val catalogMutex = Mutex()
    private var cached: CatalogSnapshot? = null

    init {
        require(apiKey.isNotBlank())
        require(country.matches(Regex("[A-Z]{2}")))
        require(callingCode.matches(Regex("[1-9][0-9]{0,3}")))
        require(
            listOf(topupProductId, packagesProductId).all {
                it.matches(Regex("[a-zA-Z0-9_-]{1,128}"))
            }
        )
    }

    override suspend fun catalog(): List<SupplierCatalog> =
        snapshot().catalog?.let(::listOf).orEmpty()

    override suspend fun prepare(
        request: HubServiceOrderRequest,
        beforeSubmit: suspend () -> Unit
    ): SupplierPreparedOrder {
        val current = snapshot(force = true)
        val catalog = current.catalog ?: throw ServiceRequestRejected("service_unavailable")
        if (request.widgetId != catalog.serviceId) {
            throw ServiceRequestRejected("service_unavailable")
        }
        if (request.revision !=
            catalog.content.revision
        ) {
            throw ServiceRequestRejected("catalog_changed")
        }
        val selection =
            current.offers[request.offerId] ?: throw ServiceRequestRejected("offer_unavailable")
        if (!request.phone.matches(Regex("\\+$callingCode[0-9]{6,12}")) ||
            request.phone.length > 16
        ) {
            throw ServiceRequestRejected("invalid_phone")
        }
        val amount = selection.offer.range?.let { range ->
            val minor = request.amountMinor?.takeIf { it.matches(Regex("[1-9][0-9]{0,17}")) }
                ?.toBigInteger() ?: throw ServiceRequestRejected("invalid_amount")
            if (minor < range.minMinor.toBigInteger() || minor > range.maxMinor.toBigInteger() ||
                (minor - range.minMinor.toBigInteger()).mod(range.stepMinor.toBigInteger()) !=
                BigInteger.ZERO
            ) {
                throw ServiceRequestRejected("invalid_amount")
            }
            HubServiceMoney(minor.toString(), range.currency, range.fractionDigits)
        } ?: selection.offer.amount.also { fixed ->
            if (request.amountMinor != null && request.amountMinor != fixed?.minor) {
                throw ServiceRequestRejected("invalid_amount")
            }
        }
        val body = buildJsonObject {
            put("payment_method", "lightning")
            put("auto_pay", false)
            put("send_email", false)
            put(
                "products",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("product_id", selection.productId)
                            put("quantity", 1)
                            put("phone_number", request.phone)
                            if (selection.packageId != null) {
                                put("package_id", selection.packageId)
                            } else {
                                val requested = requireNotNull(amount)
                                put(
                                    "value",
                                    JsonPrimitive(
                                        BigDecimal(
                                            requested.minor
                                        ).movePointLeft(requested.fractionDigits)
                                    )
                                )
                            }
                        }
                    )
                }
            )
        }
        beforeSubmit()
        val invoice = upstream("/invoices", body)
        val invoiceId = invoice.text("id")?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]{1,128}")) }
            ?: throw SupplierUnavailable()
        return SupplierPreparedOrder(
            reference = invoiceId,
            serviceTitle = catalog.content.title,
            itemTitle = selection.offer.title,
            requestedAmount = amount,
            status = invoiceStatus(invoice)
        )
    }

    override suspend fun read(reference: String): SupplierOrderStatus {
        if (!reference.matches(Regex("[a-zA-Z0-9_-]{1,128}"))) throw SupplierUnavailable()
        val invoice = upstream("/invoices/$reference")
        if (invoice.text("id") != reference) throw SupplierUnavailable()
        return invoiceStatus(invoice)
    }

    private suspend fun snapshot(force: Boolean = false): CatalogSnapshot = catalogMutex.withLock {
        cached?.takeIf { !force && clock.instant().isBefore(it.fetchedAt.plusSeconds(60)) }?.let {
            return@withLock it
        }
        val offers = linkedMapOf<String, Selection>()
        for ((productId, kind) in listOf(
            topupProductId to "topup",
            packagesProductId to "package"
        )) {
            val product = try {
                upstream("/products/$productId")
            } catch (rejected: ServiceRequestRejected) {
                if (rejected.code == "supplier_not_found") continue else throw rejected
            }
            if (product.text("id") != productId || product.text("country_code") != country ||
                (product["in_stock"] as? JsonPrimitive)?.booleanOrNull != true
            ) {
                continue
            }
            val currency = product.text("currency") ?: continue
            val digits =
                runCatching { Currency.getInstance(currency).defaultFractionDigits }.getOrNull()
                    ?.takeIf { it in 0..3 } ?: continue
            if (kind == "topup") {
                val range = product["range"] as? JsonObject
                if (range != null) {
                    val minimum = range["min"]?.exactMinor(digits)
                    val maximum = range["max"]?.exactMinor(digits)
                    val step = range["step"]?.exactMinor(digits)
                    if (minimum != null && maximum != null && step != null &&
                        minimum > BigInteger.ZERO &&
                        maximum >= minimum && step > BigInteger.ZERO
                    ) {
                        val offerId = "offer-${digest("$productId:range").take(32)}"
                        offers[offerId] = Selection(
                            productId,
                            null,
                            HubServiceOffer(
                                offerId,
                                product.text("name")?.takeIf { it.isNotBlank() && it.length <= 120 }
                                    ?: "Claro",
                                kind = kind,
                                range = HubServiceAmountRange(
                                    minimum.toString(),
                                    maximum.toString(),
                                    step.toString(),
                                    currency,
                                    digits
                                )
                            )
                        )
                        continue
                    }
                }
            }
            val packages = product["packages"] as? JsonArray ?: continue
            for (entry in packages.take(64)) {
                val item = entry as? JsonObject ?: continue
                val packageId =
                    item.text("id")?.takeIf { it.isNotBlank() && it.length <= 512 } ?: continue
                val value =
                    item.text("value")?.takeIf { it.isNotBlank() && it.length <= 120 } ?: continue
                val minor = (item["amount"] ?: item["value"])?.exactMinor(digits)?.takeIf {
                    it >
                        BigInteger.ZERO
                }
                if (kind == "topup" && minor == null) continue
                val offerId = "offer-${digest("$productId:$packageId").take(32)}"
                offers[offerId] = Selection(
                    productId,
                    packageId,
                    HubServiceOffer(
                        offerId,
                        value,
                        kind = kind,
                        amount = minor?.let { HubServiceMoney(it.toString(), currency, digits) }
                    )
                )
            }
        }
        val revision = digest(json.encodeToString(offers.values.map { it.offer }))
        val catalog = if (offers.isEmpty()) {
            null
        } else {
            SupplierCatalog(
                serviceId = "service.claro-${country.lowercase()}",
                content = HubServiceContent(
                    "Claro",
                    country,
                    "+$callingCode",
                    offers.values.map {
                        it.offer
                    },
                    revision
                )
            )
        }
        CatalogSnapshot(catalog, offers, clock.instant()).also { cached = it }
    }

    private fun invoiceStatus(invoice: JsonObject): SupplierOrderStatus {
        val paymentObject = invoice["payment"] as? JsonObject
        val payment = paymentObject?.takeIf { it.text("method") == "lightning" }
            ?.text("address")?.let(::lightningPayment)
        val orders = invoice["orders"] as? JsonArray
        val orderState = (orders?.singleOrNull() as? JsonObject)?.text("status")
        val delivered = orderState == "delivered"
        val failed = orderState in setOf("failed", "refunded")
        val processing = orderState == "processing"
        val invoiceState = invoice.text("status")
        val paymentState = paymentObject?.text("status")
        val paid = delivered || processing || orderState == "refunded" ||
            paymentState in setOf("paid", "confirmed", "payment_confirmed", "complete") ||
            invoiceState in setOf("payment_confirmed", "pending")
        val paymentStatus = when {
            paid -> "paid"

            paymentState in setOf("pending", "detected", "payment_detected") ||
                invoiceState == "payment_detected" -> "pending"

            paymentState == "unpaid" || invoiceState == "unpaid" -> "unpaid"

            else -> "unknown"
        }
        val fulfillment = when {
            delivered -> "delivered"
            failed -> "failed"
            processing -> "processing"
            orderState == "created" -> "pending"
            else -> "unknown"
        }
        val state = when {
            delivered -> "delivered"

            failed || invoiceState in setOf("denied", "blocked") -> "failed"

            paymentStatus == "paid" || paymentStatus == "pending" -> "processing"

            paymentStatus == "unpaid" && payment != null ->
                if (clock.instant().isBefore(
                        Instant.parse(payment.expiresAt)
                    )
                ) {
                    "awaiting_payment"
                } else {
                    "expired"
                }

            else -> "unknown"
        }
        return SupplierOrderStatus(state, paymentStatus, fulfillment, payment)
    }

    private fun lightningPayment(encoded: String): HubServiceLightningPayment? = runCatching {
        require(
            Regex("^lnbc[0-9]", RegexOption.IGNORE_CASE).containsMatchIn(encoded) &&
                encoded.length <= 8192
        )
        val decoded = PaymentRequest.read(encoded)
        val invoice = when (decoded) {
            is Try.Success -> decoded.result as? Bolt11Invoice
            else -> null
        } ?: return null
        val amount = invoice.amount?.msat?.takeIf { it > 0 } ?: return null
        val expires = Math.addExact(
            invoice.timestampSeconds,
            invoice.expirySeconds ?: Bolt11Invoice.DEFAULT_EXPIRY_SECONDS.toLong()
        )
        HubServiceLightningPayment(
            encoded,
            amount.toString(),
            Instant.ofEpochSecond(expires).toString()
        )
    }.getOrNull()

    private suspend fun upstream(path: String, body: JsonObject? = null): JsonObject {
        try {
            return client.prepareRequest(baseUrl + path) {
                expectSuccess = false
                method = if (body == null) HttpMethod.Get else HttpMethod.Post
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }.execute { response ->
                if (response.status ==
                    HttpStatusCode.NotFound
                ) {
                    throw ServiceRequestRejected("supplier_not_found")
                }
                if (body != null && response.status.value in setOf(400, 401, 403, 422)) {
                    throw ServiceRequestRejected("supplier_rejected")
                }
                if (!response.status.isSuccess()) throw SupplierUnavailable()
                val bytes = ByteArray(1_048_577)
                val channel = response.bodyAsChannel()
                var length = 0
                while (length < bytes.size) {
                    val count = channel.readAvailable(bytes, length, bytes.size - length)
                    if (count < 0) break
                    length += count
                }
                if (length >= bytes.size) throw SupplierUnavailable()
                val envelope = json.parseToJsonElement(
                    bytes.decodeToString(endIndex = length, throwOnInvalidSequence = true)
                ) as? JsonObject
                envelope?.get("data") as? JsonObject ?: throw SupplierUnavailable()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (rejected: ServiceRequestRejected) {
            throw rejected
        } catch (_: Exception) {
            throw SupplierUnavailable()
        }
    }

    private data class Selection(
        val productId: String,
        val packageId: String?,
        val offer: HubServiceOffer
    )
    private data class CatalogSnapshot(
        val catalog: SupplierCatalog?,
        val offers: Map<String, Selection>,
        val fetchedAt: Instant
    )
}

internal fun JsonElement.exactMinor(fractionDigits: Int): BigInteger? = runCatching {
    val text = (this as? JsonPrimitive)?.contentOrNull ?: return null
    if (text.length > 80) return null
    val decimal = BigDecimal(text)
    if (decimal.precision() > 40 || decimal.scale() !in -18..18) return null
    decimal.movePointRight(fractionDigits).setScale(0, RoundingMode.UNNECESSARY).toBigIntegerExact()
        .takeIf { it.abs().toString().length <= 18 }
}.getOrNull()

internal fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
