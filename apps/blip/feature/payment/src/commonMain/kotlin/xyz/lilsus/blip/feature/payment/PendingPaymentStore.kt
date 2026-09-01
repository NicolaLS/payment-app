package xyz.lilsus.blip.feature.payment

import com.russhwolf.settings.Settings
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.PaymentRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey

internal class PendingPaymentStore(
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun load(): List<PendingRecord> {
        val encoded = settings.getStringOrNull(STORED_PAYMENTS_KEY) ?: return emptyList()
        return runCatching {
            json.decodeFromString<StoredPendingPayments>(encoded)
                .records
                .mapNotNull(StoredPendingRecord::toPendingRecord)
        }.getOrElse {
            settings.remove(STORED_PAYMENTS_KEY)
            emptyList()
        }
    }

    fun save(records: Collection<PendingRecord>) {
        if (records.isEmpty()) {
            clear()
            return
        }
        settings.putString(
            STORED_PAYMENTS_KEY,
            json.encodeToString(
                StoredPendingPayments(
                    records =
                        records
                            .sortedBy(PendingRecord::createdAtMs)
                            .map(PendingRecord::toStored)
                )
            )
        )
    }

    fun clear() {
        settings.remove(STORED_PAYMENTS_KEY)
    }

    private companion object {
        const val STORED_PAYMENTS_KEY = "payments.pendingAttempts.v1"
    }
}

@Serializable
private data class StoredPendingPayments(val records: List<StoredPendingRecord>)

@Serializable
private data class StoredPendingRecord(
    val id: String,
    val invoice: String,
    val amountMsats: Long,
    val amountOverrideMsats: Long?,
    val origin: String,
    val createdAtMs: Long,
    val dynamicSourceKey: String?,
    val guardsDynamicSource: Boolean,
    val status: String,
    val paidMsats: Long?,
    val feeMsats: Long?,
    val wasAlreadyPaid: Boolean
)

private fun PendingRecord.toStored(): StoredPendingRecord = StoredPendingRecord(
    id = id,
    invoice = summary.write(),
    amountMsats = amountMsats,
    amountOverrideMsats = amountOverrideMsats,
    origin = origin.name,
    createdAtMs = createdAtMs,
    dynamicSourceKey = dynamicSourceKey?.value,
    guardsDynamicSource = guardsDynamicSource,
    status = status.name,
    paidMsats = paidMsats,
    feeMsats = feeMsats,
    wasAlreadyPaid = wasAlreadyPaid
)

private fun StoredPendingRecord.toPendingRecord(): PendingRecord? {
    val summary =
        when (val decoded = PaymentRequest.read(invoice)) {
            is Try.Success -> decoded.result as? Bolt11Invoice
            is Try.Failure -> null
        } ?: return null
    val restoredStatus = PendingStatus.entries.firstOrNull { it.name == status } ?: return null
    val restoredOrigin = PendingOrigin.entries.firstOrNull { it.name == origin } ?: return null
    val restoredError =
        when (restoredStatus) {
            PendingStatus.Sending,
            PendingStatus.StatusUnknown ->
                PaymentUiError.Unexpected("Payment status is unknown after app restart")

            PendingStatus.Failure -> PaymentUiError.Unexpected("Previous payment failed")

            else -> null
        }
    return PendingRecord(
        id = id,
        summary = summary,
        amountMsats = amountMsats,
        amountOverrideMsats = amountOverrideMsats,
        origin = restoredOrigin,
        createdAtMs = createdAtMs,
        dynamicSourceKey =
            dynamicSourceKey?.takeIf(String::isNotBlank)?.let(::DynamicPaymentSourceKey),
        guardsDynamicSource = guardsDynamicSource,
        paymentHashHex = summary.paymentHash.toHex(),
        status =
            if (restoredStatus == PendingStatus.Sending) {
                PendingStatus.StatusUnknown
            } else {
                restoredStatus
            },
        error = restoredError,
        paidMsats = paidMsats,
        feeMsats = feeMsats,
        visible = true,
        wasAlreadyPaid = wasAlreadyPaid,
        preimage = null
    )
}
