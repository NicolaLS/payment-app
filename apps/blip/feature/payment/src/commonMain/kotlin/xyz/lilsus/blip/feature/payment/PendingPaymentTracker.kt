package xyz.lilsus.blip.feature.payment

import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey

internal class PendingPaymentTracker(
    private val currencyManager: PaymentCurrencyManager,
    private val scope: CoroutineScope,
    private val showEstimatedFeeHint: Boolean,
    private val clock: () -> Long = ::platformCurrentTimeMillis
) {
    private val records = MutableStateFlow<Map<String, PendingRecord>>(emptyMap())
    private val visibilityJobs = mutableMapOf<String, Job>()
    private var nextRecordSequence = 0L

    private val mutableDisplayItems = MutableStateFlow<List<SessionTransactionItem>>(emptyList())
    val displayItems: StateFlow<List<SessionTransactionItem>> = mutableDisplayItems.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PendingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PendingEvent> = mutableEvents.asSharedFlow()

    fun register(
        summary: Bolt11Invoice,
        amountMsats: Long,
        amountOverrideMsats: Long?,
        origin: PendingOrigin,
        dynamicSourceKey: DynamicPaymentSourceKey? = null,
        replacesDynamicGuardId: String? = null
    ): String {
        val id = "payment-${clock()}-${nextRecordSequence++}"
        val record =
            PendingRecord(
                id = id,
                summary = summary,
                amountMsats = amountMsats,
                amountOverrideMsats = amountOverrideMsats,
                origin = origin,
                createdAtMs = clock(),
                dynamicSourceKey = dynamicSourceKey,
                guardsDynamicSource = dynamicSourceKey != null,
                paymentHashHex = summary.paymentHash.toHex()
            )

        records.update { current ->
            val acknowledged =
                replacesDynamicGuardId?.let { replacedId ->
                    current[replacedId]?.let { replaced ->
                        current + (replacedId to replaced.copy(guardsDynamicSource = false))
                    } ?: current
                } ?: current
            acknowledged + (id to record)
        }
        refreshDisplayItems()
        scheduleVisibility(id)
        return id
    }

    fun get(id: String): PendingRecord? = records.value[id]

    fun findUnresolvedByPaymentHash(paymentHashHex: String): PendingRecord? = records.value.values
        .filter {
            it.isUnresolved() &&
                it.paymentHashHex.equals(paymentHashHex, ignoreCase = true)
        }
        .maxByOrNull(PendingRecord::createdAtMs)

    fun findUnresolvedByDynamicSourceKey(
        dynamicSourceKey: DynamicPaymentSourceKey
    ): PendingRecord? = records.value.values
        .filter {
            it.isUnresolved() &&
                it.guardsDynamicSource &&
                it.dynamicSourceKey == dynamicSourceKey
        }
        .maxByOrNull(PendingRecord::createdAtMs)

    fun markSuccess(
        id: String,
        paidMsats: Long,
        feeMsats: Long,
        wasAlreadyPaid: Boolean = false,
        preimage: String? = null
    ) {
        updateStatus(
            id = id,
            status = if (wasAlreadyPaid) PendingStatus.AlreadyPaid else PendingStatus.Success,
            paidMsats = paidMsats,
            feeMsats = feeMsats,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimage,
            visible = true
        )
    }

    fun markFailure(id: String, error: PaymentUiError) {
        updateStatus(
            id = id,
            status = PendingStatus.Failure,
            error = error,
            visible = true
        )
    }

    fun markPendingInBlink(id: String) {
        updateStatus(id = id, status = PendingStatus.PendingInBlink, visible = true)
    }

    fun markStatusUnknown(id: String, error: PaymentUiError) {
        updateStatus(
            id = id,
            status = PendingStatus.StatusUnknown,
            error = error,
            visible = true
        )
    }

    fun markSending(id: String) {
        updateStatus(id = id, status = PendingStatus.Sending, error = null, visible = true)
    }

    fun makeVisible(id: String) {
        records.update { all ->
            all[id]?.let { record ->
                if (record.visible) all else all + (id to record.copy(visible = true))
            } ?: all
        }
        refreshDisplayItems()
    }

    fun refreshDisplayItems() {
        val currencyState = currencyManager.state.value
        mutableDisplayItems.value =
            records.value.values
                .filter(PendingRecord::visible)
                .sortedByDescending(PendingRecord::createdAtMs)
                .map { record ->
                    val resultAmountMsats = record.paidMsats ?: record.amountMsats
                    SessionTransactionItem(
                        id = record.id,
                        amount =
                            currencyManager.convertMsatsToDisplay(
                                record.amountMsats,
                                currencyState
                            ),
                        status = record.status,
                        createdAtMs = record.createdAtMs,
                        resultAmount =
                            currencyManager.convertMsatsToDisplay(
                                resultAmountMsats,
                                currencyState
                            ),
                        fee =
                            record.feeMsats?.let { fee ->
                                currencyManager.convertMsatsToDisplay(fee, currencyState)
                            },
                        error = record.error,
                        errorMessage = record.error?.shortMessage(),
                        showEstimatedFeeHint = showEstimatedFeeHint,
                        wasAlreadyPaid = record.wasAlreadyPaid,
                        preimage = record.preimage
                    )
                }
    }

    fun close() {
        visibilityJobs.values.forEach(Job::cancel)
        visibilityJobs.clear()
    }

    fun resetSession() {
        close()
        records.value = emptyMap()
        mutableDisplayItems.value = emptyList()
        nextRecordSequence = 0L
    }

    private fun scheduleVisibility(id: String) {
        visibilityJobs.remove(id)?.cancel()
        visibilityJobs[id] =
            scope.launch {
                delay(PENDING_NOTICE_DELAY_MS)
                val current = records.value[id]
                if (current?.status == PendingStatus.Sending) {
                    makeVisible(id)
                    mutableEvents.tryEmit(PendingEvent.BecameVisible(id))
                }
            }
    }

    private fun updateStatus(
        id: String,
        status: PendingStatus,
        error: PaymentUiError? = null,
        paidMsats: Long? = null,
        feeMsats: Long? = null,
        visible: Boolean? = null,
        wasAlreadyPaid: Boolean? = null,
        preimage: String? = null
    ) {
        records.update { all ->
            all[id]?.let { record ->
                all +
                    (
                        id to
                            record.copy(
                                status = status,
                                error = error,
                                paidMsats = paidMsats ?: record.paidMsats,
                                feeMsats = feeMsats ?: record.feeMsats,
                                visible = visible ?: record.visible,
                                wasAlreadyPaid = wasAlreadyPaid ?: record.wasAlreadyPaid,
                                preimage = preimage ?: record.preimage
                            )
                        )
            } ?: all
        }
        if (status != PendingStatus.Sending) {
            visibilityJobs.remove(id)?.cancel()
        }
        if (!status.isUnresolved()) {
            pruneResolvedRecords()
        }
        refreshDisplayItems()
    }

    private fun pruneResolvedRecords() {
        val retainedResolvedIds =
            records.value.values
                .filterNot(PendingRecord::isUnresolved)
                .sortedByDescending(PendingRecord::createdAtMs)
                .take(MAX_RESOLVED_SESSION_PAYMENTS)
                .mapTo(mutableSetOf(), PendingRecord::id)
        records.update { all ->
            all.filterValues { record ->
                record.isUnresolved() || record.id in retainedResolvedIds
            }
        }
    }

    private fun PaymentUiError.shortMessage(): String? = when (this) {
        is PaymentUiError.Blink -> error.shortMessage()
        is PaymentUiError.InvalidInvoice -> reason
        is PaymentUiError.Lnurl -> reason
        is PaymentUiError.Unexpected -> detail
    }

    private fun xyz.lilsus.blip.integration.blink.BlinkApiError.shortMessage(): String? =
        when (this) {
            is xyz.lilsus.blip.integration.blink.BlinkApiError.PaymentRejected ->
                message ?: code

            is xyz.lilsus.blip.integration.blink.BlinkApiError.Unexpected -> message

            is xyz.lilsus.blip.integration.blink.BlinkApiError.BlinkError -> type.name

            else -> null
        }

    private companion object {
        const val PENDING_NOTICE_DELAY_MS = 5_000L
        const val MAX_RESOLVED_SESSION_PAYMENTS = 10
    }
}

internal sealed interface PendingEvent {
    data class BecameVisible(val id: String) : PendingEvent
}

internal enum class PendingOrigin {
    Invoice,
    ManualEntry,
    LnurlFixed,
    LnurlManual
}

internal data class PendingRecord(
    val id: String,
    val summary: Bolt11Invoice,
    val amountMsats: Long,
    val amountOverrideMsats: Long?,
    val origin: PendingOrigin,
    val createdAtMs: Long,
    val dynamicSourceKey: DynamicPaymentSourceKey?,
    val guardsDynamicSource: Boolean,
    val paymentHashHex: String,
    val status: PendingStatus = PendingStatus.Sending,
    val error: PaymentUiError? = null,
    val paidMsats: Long? = null,
    val feeMsats: Long? = null,
    val visible: Boolean = false,
    val wasAlreadyPaid: Boolean = false,
    val preimage: String? = null
) {
    fun isUnresolved(): Boolean = status.isUnresolved()
}

private fun PendingStatus.isUnresolved(): Boolean = this == PendingStatus.Sending ||
    this == PendingStatus.PendingInBlink ||
    this == PendingStatus.StatusUnknown

internal expect fun platformCurrentTimeMillis(): Long
