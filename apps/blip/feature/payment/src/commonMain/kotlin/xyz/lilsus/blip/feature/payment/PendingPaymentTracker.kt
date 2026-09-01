package xyz.lilsus.blip.feature.payment

import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import kotlin.time.Clock
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
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentCurrencyManager

internal class PendingPaymentTracker(
    private val currencyManager: PaymentCurrencyManager,
    private val scope: CoroutineScope,
    private val showEstimatedFeeHint: Boolean,
    private val store: PendingPaymentStore,
    private val clock: () -> Long = ::currentTimeMillis
) {
    private val records =
        MutableStateFlow(
            store.load().associateBy(PendingRecord::id)
        )
    private val visibilityJobs = mutableMapOf<String, Job>()
    private var nextRecordSequence = 0L
    private var focusedRecordId: String? = null

    private val mutableDisplayItems = MutableStateFlow<List<SessionTransactionItem>>(emptyList())
    val displayItems: StateFlow<List<SessionTransactionItem>> = mutableDisplayItems.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PendingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PendingEvent> = mutableEvents.asSharedFlow()

    init {
        nextRecordSequence = records.value.size.toLong()
        persistRecords()
        refreshDisplayItems()
    }

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
        focusedRecordId = null
        persistRecords()
        refreshDisplayItems()
        scheduleVisibility(id)
        return id
    }

    fun get(id: String): PendingRecord? = records.value[id]

    fun findLatestByPaymentHash(paymentHashHex: String): PendingRecord? = records.value.values
        .filter {
            it.paymentHashHex.equals(paymentHashHex, ignoreCase = true)
        }
        .maxByOrNull(PendingRecord::createdAtMs)

    fun findGuardingByDynamicSourceKey(dynamicSourceKey: DynamicPaymentSourceKey): PendingRecord? =
        records.value.values
            .filter {
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
            guardsDynamicSource = false,
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
        val record = records.value[id] ?: return
        updateStatus(
            id = id,
            status = PendingStatus.Sending,
            error = null,
            visible = true,
            guardsDynamicSource = record.dynamicSourceKey != null
        )
    }

    fun makeVisible(id: String) {
        records.update { all ->
            all[id]?.let { record ->
                if (record.visible) all else all + (id to record.copy(visible = true))
            } ?: all
        }
        persistRecords()
        refreshDisplayItems()
    }

    fun focus(id: String) {
        if (records.value[id] == null) return
        focusedRecordId = id
        refreshDisplayItems()
    }

    fun refreshDisplayItems() {
        val currencyState = currencyManager.state.value
        mutableDisplayItems.value =
            recordsForDisplay()
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
        focusedRecordId = null
        store.clear()
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
        preimage: String? = null,
        guardsDynamicSource: Boolean? = null
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
                                preimage = preimage ?: record.preimage,
                                guardsDynamicSource =
                                    guardsDynamicSource ?: record.guardsDynamicSource
                            )
                        )
            } ?: all
        }
        if (status != PendingStatus.Sending) {
            visibilityJobs.remove(id)?.cancel()
        }
        persistRecords()
        refreshDisplayItems()
    }

    private fun persistRecords() {
        store.save(records.value.values)
    }

    private fun recordsForDisplay(): List<PendingRecord> {
        val visibleRecords = records.value.values.filter(PendingRecord::visible)
        val unresolved = visibleRecords.filter(PendingRecord::isUnresolved)
        val resolved = visibleRecords.filterNot(PendingRecord::isUnresolved)
        val focused = resolved.firstOrNull { it.id == focusedRecordId }
        val recentResolved =
            buildList {
                focused?.let(::add)
                resolved
                    .asSequence()
                    .filterNot { it.id == focusedRecordId }
                    .sortedByDescending(PendingRecord::createdAtMs)
                    .take(MAX_RESOLVED_SESSION_PAYMENTS - size)
                    .forEach(::add)
            }
        return (unresolved + recentResolved).sortedByDescending(PendingRecord::createdAtMs)
    }

    private fun PaymentUiError.shortMessage(): String? = when (this) {
        is PaymentUiError.Blink -> error.shortMessage()
        is PaymentUiError.InvalidInvoice -> reason
        is PaymentUiError.Lnurl -> reason
        is PaymentUiError.ExchangeRateUnavailable -> "Exchange rate unavailable for $currencyCode"
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

internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
