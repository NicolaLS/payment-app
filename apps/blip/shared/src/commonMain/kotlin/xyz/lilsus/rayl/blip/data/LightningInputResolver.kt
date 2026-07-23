package xyz.lilsus.rayl.blip.data

import com.eygraber.uri.Uri
import fr.acinq.bitcoin.Bech32
import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.wire.OfferTypes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import xyz.lilsus.rayl.blip.domain.AppClock
import xyz.lilsus.rayl.blip.domain.PaymentDraft
import xyz.lilsus.rayl.blip.domain.PaymentFailure
import xyz.lilsus.rayl.blip.domain.PaymentOrigin
import xyz.lilsus.rayl.blip.domain.PaymentRequestKind

sealed interface InputResolution {
    data class Ready(val draft: PaymentDraft) : InputResolution
    data class NeedsAmount(
        val invoice: Bolt11Invoice,
        val normalizedRequest: String,
        val origin: PaymentOrigin
    ) : InputResolution
    data class NeedsLnurlAmount(val request: LnurlPayRequest) : InputResolution
    data class Unsupported(val kind: UnsupportedInput) : InputResolution
    data class Rejected(val failure: PaymentFailure) : InputResolution
}

enum class UnsupportedInput {
    Bolt12,
    OnChain,
    LnurlWithdraw,
    Nwc
}

data class LnurlPayRequest(
    val callback: String,
    val minSendable: MilliSatoshi,
    val maxSendable: MilliSatoshi,
    val metadataRaw: String,
    val description: String?,
    val commentAllowed: Int,
    val originalRequest: String,
    val origin: PaymentOrigin
)

class LightningInputResolver(
    private val httpClient: HttpClient,
    private val clock: AppClock,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
) {
    suspend fun resolve(input: String, origin: PaymentOrigin): InputResolution {
        val normalized = normalizeInput(input)
            ?: return InputResolution.Rejected(PaymentFailure.InvalidRequest)

        return try {
            when {
                normalized.startsWith("nostr+walletconnect:", ignoreCase = true) ->
                    InputResolution.Unsupported(UnsupportedInput.Nwc)

                normalized.startsWith("bitcoin:", ignoreCase = true) ->
                    resolveBitcoinUri(normalized, origin)

                normalized.startsWith("lnurlw", ignoreCase = true) ->
                    InputResolution.Unsupported(UnsupportedInput.LnurlWithdraw)

                normalized.startsWith("lnurl", ignoreCase = true) ->
                    resolveLnurl(decodeLnurl(normalized), normalized, origin)

                isLightningAddress(normalized) ->
                    resolveLnurl(lightningAddressUrl(normalized), normalized, origin)

                normalized.startsWith("lno", ignoreCase = true) ->
                    classifyOffer(normalized)

                else -> resolveInvoice(
                    normalized,
                    normalized,
                    origin,
                    PaymentRequestKind.FixedInvoice
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: LnurlNetworkException) {
            InputResolution.Rejected(PaymentFailure.NetworkUnavailable)
        } catch (_: Throwable) {
            InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }
    }

    fun withAmount(
        invoice: Bolt11Invoice,
        normalizedRequest: String,
        amount: MilliSatoshi,
        origin: PaymentOrigin
    ): InputResolution {
        if (amount.msat <= 0L) {
            return InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }
        return invoicePolicy(invoice)?.let(InputResolution::Rejected)
            ?: InputResolution.Ready(
                PaymentDraft(
                    invoice = invoice,
                    originalRequest = normalizedRequest,
                    amount = amount,
                    memo = invoice.description,
                    origin = origin,
                    requestKind = PaymentRequestKind.FixedInvoice
                )
            )
    }

    suspend fun requestLnurlInvoice(
        request: LnurlPayRequest,
        amount: MilliSatoshi,
        comment: String?
    ): InputResolution {
        if (amount.msat !in request.minSendable.msat..request.maxSendable.msat) {
            return InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }
        val normalizedComment = comment?.trim()?.takeIf(String::isNotEmpty)
        if (normalizedComment != null && normalizedComment.length > request.commentAllowed) {
            return InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }

        return try {
            val callback = Url(request.callback)
            requirePublicHttps(callback)
            val callbackUrl = buildString {
                append(callback)
                append(if (callback.parameters.isEmpty()) '?' else '&')
                append("amount=")
                append(amount.msat)
                if (normalizedComment != null) {
                    append("&comment=")
                    append(Uri.encode(normalizedComment))
                }
            }
            val payload = getJson(callbackUrl)
            val response = json.decodeFromString<LnurlInvoiceResponse>(payload)
            response.reason?.let {
                return InputResolution.Rejected(PaymentFailure.ProviderRejected("LNURL_ERROR"))
            }
            val invoiceString = response.paymentRequest
                ?: return InputResolution.Rejected(PaymentFailure.InvalidRequest)
            val invoice = parseBolt11(invoiceString)
                ?: return InputResolution.Rejected(PaymentFailure.InvalidRequest)
            invoicePolicy(invoice)?.let { return InputResolution.Rejected(it) }
            if (invoice.amount?.msat != amount.msat) {
                return InputResolution.Rejected(PaymentFailure.InvalidRequest)
            }

            val expectedHash = Crypto.sha256(ByteVector(request.metadataRaw.encodeToByteArray()))
            if (invoice.descriptionHash != expectedHash) {
                return InputResolution.Rejected(PaymentFailure.InvalidRequest)
            }

            InputResolution.Ready(
                PaymentDraft(
                    invoice = invoice,
                    originalRequest = request.originalRequest,
                    amount = amount,
                    memo = request.description,
                    origin = request.origin,
                    requestKind = PaymentRequestKind.DynamicRequest
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: LnurlNetworkException) {
            InputResolution.Rejected(PaymentFailure.NetworkUnavailable)
        } catch (_: Throwable) {
            InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }
    }

    private suspend fun resolveBitcoinUri(value: String, origin: PaymentOrigin): InputResolution {
        val uri = Uri.parseOrNull(value)
            ?: return InputResolution.Rejected(PaymentFailure.InvalidRequest)
        val lightning = uri.getQueryParameter("lightning")
        return if (lightning.isNullOrBlank()) {
            InputResolution.Unsupported(UnsupportedInput.OnChain)
        } else {
            resolveInvoice(
                value = lightning,
                originalRequest = value,
                origin = origin,
                kind = PaymentRequestKind.FixedInvoice
            )
        }
    }

    private fun classifyOffer(value: String): InputResolution =
        if (runCatching { OfferTypes.Offer.decode(value).get() }.isSuccess) {
            InputResolution.Unsupported(UnsupportedInput.Bolt12)
        } else {
            InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }

    private fun resolveInvoice(
        value: String,
        originalRequest: String,
        origin: PaymentOrigin,
        kind: PaymentRequestKind
    ): InputResolution {
        val invoice = parseBolt11(value)
            ?: return InputResolution.Rejected(PaymentFailure.InvalidRequest)
        invoicePolicy(invoice)?.let { return InputResolution.Rejected(it) }
        val amount = invoice.amount
            ?: return InputResolution.NeedsAmount(invoice, originalRequest, origin)
        if (amount.msat <= 0L) {
            return InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }
        return InputResolution.Ready(
            PaymentDraft(
                invoice = invoice,
                originalRequest = originalRequest,
                amount = amount,
                memo = invoice.description,
                origin = origin,
                requestKind = kind
            )
        )
    }

    private suspend fun resolveLnurl(
        endpoint: String,
        originalRequest: String,
        origin: PaymentOrigin
    ): InputResolution {
        val payload = getJson(endpoint)
        val response = json.decodeFromString<LnurlPayResponse>(payload)
        response.reason?.let {
            return InputResolution.Rejected(PaymentFailure.ProviderRejected("LNURL_ERROR"))
        }
        if (response.tag == "withdrawRequest") {
            return InputResolution.Unsupported(UnsupportedInput.LnurlWithdraw)
        }
        if (response.tag != "payRequest" ||
            response.callback.isNullOrBlank() ||
            response.metadata.isNullOrBlank() ||
            response.payerData != null ||
            response.minSendable == null ||
            response.maxSendable == null ||
            response.minSendable <= 0L ||
            response.maxSendable < response.minSendable
        ) {
            return InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }
        val callback = Url(response.callback)
        requirePublicHttps(callback)
        if (
            callback.parameters.contains("amount") ||
            callback.parameters.contains("comment") ||
            callback.parameters.contains("payerdata")
        ) {
            return InputResolution.Rejected(PaymentFailure.InvalidRequest)
        }
        val description = parseDescription(response.metadata)
        return InputResolution.NeedsLnurlAmount(
            LnurlPayRequest(
                callback = response.callback,
                minSendable = MilliSatoshi(response.minSendable),
                maxSendable = MilliSatoshi(response.maxSendable),
                metadataRaw = response.metadata,
                description = description,
                commentAllowed = (response.commentAllowed ?: 0).coerceIn(0, MAX_COMMENT_LENGTH),
                originalRequest = originalRequest,
                origin = origin
            )
        )
    }

    private suspend fun getJson(url: String): String {
        var current = Url(url)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            requirePublicHttps(current)
            val response = try {
                httpClient.get(current)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                throw LnurlNetworkException()
            }
            if (response.status in REDIRECT_STATUSES) {
                if (redirectCount == MAX_REDIRECTS) throw LnurlNetworkException()
                val location = response.headers[HttpHeaders.Location]
                    ?: throw LnurlNetworkException()
                val redirect = runCatching { Url(location) }.getOrNull()
                    ?: throw LnurlNetworkException()
                requirePublicHttps(redirect)
                current = redirect
                return@repeat
            }
            if (!response.status.isSuccess()) {
                throw LnurlNetworkException()
            }
            val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (declaredLength != null && declaredLength > MAX_RESPONSE_BYTES) {
                throw LnurlNetworkException()
            }
            val contentType = response.headers[HttpHeaders.ContentType]?.lowercase()
                ?: throw LnurlNetworkException()
            if (!contentType.startsWith("application/json") &&
                !contentType.startsWith("text/json")
            ) {
                throw LnurlNetworkException()
            }
            val body = response.body<String>()
            if (body.encodeToByteArray().size > MAX_RESPONSE_BYTES) {
                throw LnurlNetworkException()
            }
            return body
        }
        throw LnurlNetworkException()
    }

    private fun parseBolt11(value: String): Bolt11Invoice? =
        runCatching { PaymentRequest.read(value).get() as? Bolt11Invoice }.getOrNull()

    private fun invoicePolicy(invoice: Bolt11Invoice): PaymentFailure? = when {
        invoice.chain != Chain.Mainnet -> PaymentFailure.WrongNetwork
        invoice.isExpired(clock.nowSeconds()) -> PaymentFailure.ExpiredInvoice
        else -> null
    }

    private fun decodeLnurl(value: String): String {
        val (hrp, bytes, encoding) = Bech32.decodeBytes(value)
        require(hrp.equals("lnurl", ignoreCase = true))
        require(encoding == Bech32.Encoding.Bech32)
        return bytes.decodeToString()
    }

    private fun lightningAddressUrl(value: String): String {
        val local = value.substringBefore('@')
        val domain = value.substringAfter('@')
        require(local.isNotBlank() && domain.isNotBlank())
        require(domain.none { it == '/' || it == '?' || it == '#' })
        return "https://$domain/.well-known/lnurlp/${Uri.encode(local)}"
    }

    private fun requirePublicHttps(url: Url) {
        require(url.protocol.name.equals("https", ignoreCase = true))
        require(url.user == null && url.password == null)
        val host = url.host.lowercase().trimEnd('.')
        require(host.isNotBlank())
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local"))
        require(!isPrivateIpv4(host))
        require(host != "::1" && !host.startsWith("fc") && !host.startsWith("fd"))
        require(!host.startsWith("fe80:"))
    }

    private fun parseDescription(metadata: String): String? = runCatching {
        json.parseToJsonElement(metadata)
            .let { it as? JsonArray }
            ?.firstNotNullOfOrNull { row ->
                val fields = row as? JsonArray ?: return@firstNotNullOfOrNull null
                if (fields.getOrNull(0)?.jsonPrimitive?.content == "text/plain") {
                    fields.getOrNull(1)?.jsonPrimitive?.content
                } else {
                    null
                }
            }
    }.getOrNull()

    private fun normalizeInput(input: String): String? {
        var value = input.trim()
        if (value.startsWith("lightning:", ignoreCase = true)) {
            value = value.substringAfter(':').trim()
        }
        if (value.startsWith("lnurl:", ignoreCase = true)) {
            value = value.substringAfter(':').trimStart('/').trim()
        }
        return value.takeIf { it.isNotBlank() && it.length <= MAX_INPUT_LENGTH }
    }

    private fun isLightningAddress(value: String): Boolean = value.count { it == '@' } == 1 &&
        value.none(Char::isWhitespace) &&
        !value.contains("://")

    private fun isPrivateIpv4(host: String): Boolean {
        val octets = host.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        return (octets[0] == 10) ||
            (octets[0] == 127) ||
            (octets[0] == 0) ||
            (octets[0] == 169 && octets[1] == 254) ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    companion object {
        private const val MAX_INPUT_LENGTH = 16_384
        private const val MAX_RESPONSE_BYTES = 256 * 1_024
        private const val MAX_COMMENT_LENGTH = 1_000
        private const val MAX_REDIRECTS = 3
        private val REDIRECT_STATUSES = setOf(
            HttpStatusCode.MovedPermanently,
            HttpStatusCode.Found,
            HttpStatusCode.SeeOther,
            HttpStatusCode.TemporaryRedirect,
            HttpStatusCode.PermanentRedirect
        )
    }
}

@Serializable
private data class LnurlPayResponse(
    val status: String? = null,
    val reason: String? = null,
    val tag: String? = null,
    val callback: String? = null,
    @SerialName("minSendable") val minSendable: Long? = null,
    @SerialName("maxSendable") val maxSendable: Long? = null,
    val metadata: String? = null,
    @SerialName("commentAllowed") val commentAllowed: Int? = null,
    @SerialName("payerData") val payerData: JsonElement? = null
)

@Serializable
private data class LnurlInvoiceResponse(
    val status: String? = null,
    val reason: String? = null,
    @SerialName("pr") val paymentRequest: String? = null
)

private class LnurlNetworkException : RuntimeException()
