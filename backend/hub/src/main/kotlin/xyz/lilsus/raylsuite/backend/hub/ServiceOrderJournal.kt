package xyz.lilsus.raylsuite.backend.hub

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.hubapi.HubServiceMoney
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrder
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrderRequest

/** A locked, atomic local journal for one backend process. Its directory must survive restarts. */
class ServiceOrderJournal(
    private val directory: Path,
    private val suppliers: List<ServiceSupplier>,
    private val clock: Clock = Clock.systemUTC()
) : Closeable {
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    private val directoryPermissions = PosixFilePermissions.fromString("rwx------")
    private val filePermissions = PosixFilePermissions.fromString("rw-------")
    private val lockChannel: FileChannel
    private val processLock: java.nio.channels.FileLock

    init {
        require(suppliers.map { it.id }.distinct().size == suppliers.size)
        require(!Files.isSymbolicLink(directory)) { "Order directory cannot be a symbolic link" }
        Files.createDirectories(
            directory,
            PosixFilePermissions.asFileAttribute(directoryPermissions)
        )
        Files.setPosixFilePermissions(directory, directoryPermissions)
        val lockPath = directory.resolve(".lock")
        require(!Files.isSymbolicLink(lockPath)) { "Order lock cannot be a symbolic link" }
        lockChannel =
            FileChannel.open(
                lockPath,
                setOf(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                PosixFilePermissions.asFileAttribute(filePermissions)
            )
        Files.setPosixFilePermissions(lockPath, filePermissions)
        processLock = try {
            lockChannel.tryLock()
                ?: error("Order directory is already in use by another backend process")
        } catch (error: Exception) {
            lockChannel.close()
            throw error
        }
    }

    suspend fun put(
        orderId: String,
        token: String,
        request: HubServiceOrderRequest
    ): HubServiceOrder = mutex.withLock {
        validateAccess(orderId, token)
        load(orderId)?.let { stored ->
            authenticate(stored, token)
            if (stored.request != request) throw ServiceHttpFailure(409, "order_conflict")
            return@withLock refresh(stored).order
        }
        validateRequest(request)
        val route = resolve(request)
        val now = clock.instant().toString()
        val offer = route.catalog.content.offers.firstOrNull { it.id == request.offerId }
            ?: throw ServiceHttpFailure(409, "offer_unavailable")
        if (route.catalog.offerKind(request.variantId) != offer.kind) {
            throw ServiceHttpFailure(400, "invalid_offer")
        }
        val requestedAmount = offer.range?.let { range ->
            request.amountMinor?.let { HubServiceMoney(it, range.currency, range.fractionDigits) }
        } ?: offer.amount
        var record = OrderRecord(
            tokenHash = digest(token),
            request = request,
            supplierId = route.supplier.id,
            order = HubServiceOrder(
                orderId = orderId, serviceTitle = route.catalog.content.title,
                itemTitle = offer.title, phone = request.phone, requestedAmount = requestedAmount,
                state = "preparing", paymentStatus = "unknown", fulfillmentStatus = "pending",
                createdAt = now, updatedAt = now
            )
        )
        var submitted = false
        try {
            val prepared = route.supplier.prepare(request) {
                check(!submitted) { "Supplier attempted to submit the same preparation twice" }
                write(record)
                submitted = true
            }
            check(submitted) { "Supplier omitted durable preparation boundary" }
            record = record.copy(
                supplierReference = prepared.reference,
                order = record.order.copy(
                    serviceTitle = prepared.serviceTitle,
                    itemTitle = prepared.itemTitle,
                    requestedAmount = prepared.requestedAmount,
                    state = prepared.status.state,
                    paymentStatus = prepared.status.paymentStatus,
                    fulfillmentStatus = prepared.status.fulfillmentStatus,
                    payment = prepared.status.payment,
                    updatedAt = clock.instant().toString()
                )
            )
            write(record)
            record.order
        } catch (cancellation: CancellationException) {
            // The durable preparing marker remains and becomes unknown after restart/retry.
            throw cancellation
        } catch (rejected: ServiceRequestRejected) {
            if (!submitted) throw rejected.toHttpFailure()
            record = record.copy(
                order = record.order.copy(
                    state = "failed",
                    paymentStatus = "unpaid",
                    fulfillmentStatus = "failed",
                    updatedAt = clock.instant().toString()
                )
            )
            write(record)
            record.order
        } catch (_: Exception) {
            if (!submitted) throw ServiceHttpFailure(503, "supplier_unavailable")
            record = record.copy(
                order = record.order.copy(
                    state = "unknown",
                    paymentStatus = "unknown",
                    fulfillmentStatus = "unknown",
                    updatedAt = clock.instant().toString()
                )
            )
            write(record)
            record.order
        }
    }

    suspend fun get(orderId: String, token: String): HubServiceOrder = mutex.withLock {
        validateAccess(orderId, token)
        val record = load(orderId) ?: throw ServiceHttpFailure(404, "order_not_found")
        authenticate(record, token)
        refresh(record).order
    }

    private suspend fun resolve(request: HubServiceOrderRequest): Route {
        for (supplier in suppliers) {
            val catalogs = try {
                supplier.catalog()
            } catch (_: SupplierUnavailable) {
                throw ServiceHttpFailure(503, "supplier_unavailable")
            }
            val catalog = catalogs.firstOrNull { it.widget.id == request.widgetId } ?: continue
            if (catalog.widget.variants.none { it.id == request.variantId }) {
                throw ServiceHttpFailure(409, "catalog_changed")
            }
            if (catalog.content.revision !=
                request.revision
            ) {
                throw ServiceHttpFailure(409, "catalog_changed")
            }
            return Route(supplier, catalog)
        }
        throw ServiceHttpFailure(404, "service_unavailable")
    }

    private suspend fun refresh(record: OrderRecord): OrderRecord {
        if (record.order.state in setOf("delivered", "failed")) return record
        if (record.supplierReference == null) {
            if (record.order.state == "unknown") return record
            return record.copy(
                order = record.order.copy(
                    state = "unknown",
                    paymentStatus = "unknown",
                    fulfillmentStatus = "unknown",
                    updatedAt = clock.instant().toString()
                )
            ).also(::write)
        }
        // Bound repeat polling of this order; the supplier's quota also spans other invoice IDs.
        if (clock.instant().isBefore(
                Instant.parse(record.order.updatedAt).plusSeconds(10)
            )
        ) {
            return record
        }
        val supplier = suppliers.firstOrNull { it.id == record.supplierId }
        val status = try {
            supplier?.read(record.supplierReference)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        val paymentChanged = record.order.payment != null && status?.payment != null &&
            record.order.payment != status.payment
        val nextState = when {
            paymentChanged -> "unknown"

            record.order.paymentStatus == "paid" &&
                status?.state in setOf("awaiting_payment", "expired") -> "unknown"

            else -> status?.state ?: "unknown"
        }
        val updated = record.copy(
            order = record.order.copy(
                state = nextState,
                paymentStatus = if (record.order.paymentStatus ==
                    "paid"
                ) {
                    "paid"
                } else {
                    status?.paymentStatus ?: "unknown"
                },
                fulfillmentStatus = status?.fulfillmentStatus ?: "unknown",
                payment = if (paymentChanged) {
                    record.order.payment
                } else {
                    status?.payment
                        ?: record.order.payment
                },
                updatedAt = clock.instant().toString()
            )
        )
        write(updated)
        return updated
    }

    private fun authenticate(record: OrderRecord, token: String) {
        if (!MessageDigest.isEqual(record.tokenHash.toByteArray(), digest(token).toByteArray())) {
            throw ServiceHttpFailure(401, "order_unauthorized")
        }
    }

    private fun validateAccess(orderId: String, token: String) {
        if (!runCatching { UUID.fromString(orderId).toString() == orderId }.getOrDefault(false)) {
            throw ServiceHttpFailure(400, "invalid_order_id")
        }
        if (!token.matches(
                Regex("[0-9a-f]{64}")
            )
        ) {
            throw ServiceHttpFailure(401, "order_unauthorized")
        }
    }

    private fun validateRequest(request: HubServiceOrderRequest) {
        if (listOf(request.widgetId, request.variantId, request.offerId).any {
                !it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
            } || request.widgetId.startsWith("local.") || request.revision.length !in 1..128 ||
            request.phone.length !in 7..16 || (request.amountMinor?.length ?: 0) > 18
        ) {
            throw ServiceHttpFailure(400, "invalid_request")
        }
    }

    private fun load(id: String): OrderRecord? {
        val path = directory.resolve("$id.json")
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        // A corrupt journal must never be treated as a missing order and resubmitted.
        if (Files.isSymbolicLink(path) || Files.size(path) > 65_536) {
            throw ServiceHttpFailure(503, "order_storage_unavailable")
        }
        return try {
            json.decodeFromString<OrderRecord>(Files.readString(path)).also {
                require(it.schemaVersion == 1 && it.order.orderId == id)
            }
        } catch (_: Exception) {
            throw ServiceHttpFailure(503, "order_storage_unavailable")
        }
    }

    private fun write(record: OrderRecord) {
        val path = directory.resolve("${record.order.orderId}.json")
        val temp = Files.createTempFile(
            directory,
            ".order-",
            ".tmp",
            PosixFilePermissions.asFileAttribute(filePermissions)
        )
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(
                    json.encodeToString(record).toByteArray(Charsets.UTF_8)
                )
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(
                temp,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun close() {
        processLock.release()
        lockChannel.close()
    }

    private data class Route(val supplier: ServiceSupplier, val catalog: SupplierCatalog)
}

@Serializable
private data class OrderRecord(
    val schemaVersion: Int = 1,
    val tokenHash: String,
    val request: HubServiceOrderRequest,
    val supplierId: String,
    val supplierReference: String? = null,
    val order: HubServiceOrder
)

class ServiceHttpFailure(val status: Int, val code: String) : Exception(code)

private fun ServiceRequestRejected.toHttpFailure(): ServiceHttpFailure = when (code) {
    "catalog_changed", "offer_unavailable" -> ServiceHttpFailure(409, code)
    "service_unavailable", "supplier_not_found" -> ServiceHttpFailure(404, "service_unavailable")
    "invalid_phone", "invalid_amount", "invalid_offer" -> ServiceHttpFailure(400, code)
    else -> ServiceHttpFailure(503, "supplier_unavailable")
}
