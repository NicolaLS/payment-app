package xyz.lilsus.lasr.feature.payment

import fr.acinq.lightning.payment.Bolt11Invoice
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import xyz.lilsus.lasr.integration.nwc.NwcLookupOutcome
import xyz.lilsus.lasr.integration.nwc.NwcPayOutcome
import xyz.lilsus.lasr.integration.nwc.NwcSentPayment
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentCurrencyManager

internal class PendingPaymentTracker(
    private val lookupInvoice: suspend (String, Long) -> NwcLookupOutcome,
    private val isInForeground: StateFlow<Boolean>,
    private val currencyManager: PaymentCurrencyManager,
    private val scope: CoroutineScope,
    private val showEstimatedFeeHint: Boolean,
    private val store: PendingPaymentStore,
    private val clock: () -> Long = ::currentTimeMillis,
    private val visibilityDelayMs: Long = VISIBILITY_DELAY_MS,
    private val lookupRetryDelaysMs: List<Long> = LOOKUP_RETRY_DELAYS_MS
) {
    private val records =
        MutableStateFlow(
            store.load().associateBy(PendingRecord::id)
        )
    private val visibilityJobs = mutableMapOf<String, Job>()
    private val reconciliationJobs = mutableMapOf<String, Job>()
    private var nextSequence = 0L
    private var focusedRecordId: String? = null

    private val mutableDisplayItems = MutableStateFlow<List<SessionTransactionItem>>(emptyList())
    val displayItems: StateFlow<List<SessionTransactionItem>> = mutableDisplayItems.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PendingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PendingEvent> = mutableEvents.asSharedFlow()

    init {
        nextSequence = records.value.size.toLong()
        persistRecords()
        refreshDisplayItems()
        records.value.values
            .filter { it.status == PendingStatus.Resolving }
            .forEach { startReconciliation(it.id) }
    }

    fun register(
        summary: Bolt11Invoice,
        amountMsats: Long,
        amountOverrideMsats: Long?,
        origin: PendingOrigin,
        dynamicSourceKey: DynamicPaymentSourceKey? = null,
        replacesDynamicGuardId: String? = null
    ): String {
        val id = "payment-${clock()}-${nextSequence++}"
        val record = PendingRecord(
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
        scheduleVisibility(id)
        refreshDisplayItems()
        return id
    }

    fun get(id: String): PendingRecord? = records.value[id]

    fun findLatestByPaymentHash(paymentHashHex: String): PendingRecord? = records.value.values
        .filter { it.paymentHashHex.equals(paymentHashHex, ignoreCase = true) }
        .maxByOrNull(PendingRecord::createdAtMs)

    fun findGuardingByDynamicSourceKey(dynamicSourceKey: DynamicPaymentSourceKey): PendingRecord? =
        records.value.values
            .filter {
                it.guardsDynamicSource &&
                    it.dynamicSourceKey == dynamicSourceKey
            }
            .maxByOrNull(PendingRecord::createdAtMs)

    fun applyPayOutcome(id: String, outcome: NwcPayOutcome) {
        when (outcome) {
            is NwcPayOutcome.Settled ->
                settle(id, outcome.preimage, outcome.feesPaidMsats)

            is NwcPayOutcome.WalletRejected ->
                fail(
                    id,
                    NwcPaymentError.Rejected(outcome.code, outcome.detail)
                )

            is NwcPayOutcome.DefinitelyNotSent ->
                fail(id, NwcPaymentError.DefinitelyNotSent(outcome.detail))

            is NwcPayOutcome.Uncertain -> {
                val record = records.value[id] ?: return
                if (record.visible) {
                    transition(id, PendingStatus.Resolving)
                    startReconciliation(id)
                }
            }
        }
    }

    fun applySentPayment(notification: NwcSentPayment) {
        val record = records.value.values
            .filter {
                it.status != PendingStatus.Succeeded &&
                    it.paymentHashHex.equals(notification.paymentHash, ignoreCase = true)
            }
            .maxByOrNull(PendingRecord::createdAtMs)
            ?: return
        settle(
            id = record.id,
            preimage = notification.preimage,
            feesPaidMsats = notification.feesPaidMsats
        )
    }

    fun retry(id: String): PendingRecord? {
        val record =
            records.value[id]?.takeIf {
                it.status == PendingStatus.OutcomeUnknown || it.status == PendingStatus.Failed
            } ?: return null
        transition(
            id = id,
            status = PendingStatus.Sending,
            error = null,
            guardsDynamicSource = record.dynamicSourceKey != null,
            visible = true
        )
        scheduleVisibility(id)
        return record
    }

    fun makeVisible(id: String) {
        val record = records.value[id] ?: return
        transition(id, record.status, visible = true)
    }

    fun focus(id: String) {
        if (records.value[id] == null) return
        focusedRecordId = id
        refreshDisplayItems()
    }

    fun refreshDisplayItems() {
        val currencyState = currencyManager.state.value
        mutableDisplayItems.value = recordsForDisplay()
            .map { record ->
                SessionTransactionItem(
                    id = record.id,
                    amount = currencyManager.convertMsatsToDisplay(
                        record.amountMsats,
                        currencyState
                    ),
                    status = record.status,
                    createdAtMs = record.createdAtMs,
                    resultAmount = currencyManager.convertMsatsToDisplay(
                        record.paidMsats ?: record.amountMsats,
                        currencyState
                    ),
                    fee = record.feeMsats?.let {
                        currencyManager.convertMsatsToDisplay(it, currencyState)
                    },
                    error = record.error?.let(PaymentUiError::Nwc),
                    errorMessage = record.error?.detail,
                    showEstimatedFeeHint = showEstimatedFeeHint,
                    preimage = record.preimage
                )
            }
    }

    fun resetSession() {
        close()
        records.value = emptyMap()
        mutableDisplayItems.value = emptyList()
        nextSequence = 0L
        focusedRecordId = null
        store.clear()
    }

    fun close() {
        (visibilityJobs.values + reconciliationJobs.values).forEach(Job::cancel)
        visibilityJobs.clear()
        reconciliationJobs.clear()
    }

    private fun scheduleVisibility(id: String) {
        visibilityJobs.remove(id)?.cancel()
        visibilityJobs[id] = scope.launch {
            delay(visibilityDelayMs)
            val record = records.value[id] ?: return@launch
            if (record.status == PendingStatus.Sending) {
                transition(
                    id = id,
                    status = PendingStatus.Resolving,
                    visible = true
                )
                mutableEvents.tryEmit(PendingEvent.BecameVisible(id))
                startReconciliation(id)
            }
        }
    }

    private fun startReconciliation(id: String) {
        if (reconciliationJobs[id]?.isActive == true) return
        val job = scope.launch {
            var retryIndex = 0
            while (records.value[id]?.status == PendingStatus.Resolving) {
                isInForeground.first { it }
                when (
                    val outcome = lookupInvoice(
                        requireNotNull(records.value[id]).paymentHashHex,
                        LOOKUP_TIMEOUT_MS
                    )
                ) {
                    is NwcLookupOutcome.Settled -> {
                        settle(id, outcome.preimage, outcome.feesPaidMsats)
                        return@launch
                    }

                    NwcLookupOutcome.Failed -> {
                        fail(id, NwcPaymentError.Rejected("LOOKUP_FAILED", "Payment failed"))
                        return@launch
                    }

                    is NwcLookupOutcome.PermanentlyUnavailable -> {
                        outcomeUnknown(id, outcome.detail)
                        return@launch
                    }

                    NwcLookupOutcome.Pending,
                    NwcLookupOutcome.NotFound,
                    is NwcLookupOutcome.RetryableFailure -> Unit
                }
                val delayMs = lookupRetryDelaysMs[
                    retryIndex.coerceAtMost(lookupRetryDelaysMs.lastIndex)
                ]
                retryIndex++
                delay(delayMs)
            }
        }
        job.invokeOnCompletion {
            if (reconciliationJobs[id] === job) reconciliationJobs.remove(id)
        }
        reconciliationJobs[id] = job
    }

    private fun settle(id: String, preimage: String?, feesPaidMsats: Long?) {
        val record = records.value[id] ?: return
        val paidMsats =
            record.amountOverrideMsats ?: record.summary.amount?.msat ?: record.amountMsats
        val changed = transition(
            id = id,
            status = PendingStatus.Succeeded,
            error = null,
            paidMsats = paidMsats,
            feeMsats = feesPaidMsats ?: 0L,
            preimage = preimage,
            visible = true
        )
        if (changed) {
            mutableEvents.tryEmit(
                PendingEvent.Settled(
                    id = id,
                    paidMsats = paidMsats,
                    feeMsats = feesPaidMsats ?: 0L,
                    preimage = preimage,
                    wasVisible = record.visible
                )
            )
        }
    }

    private fun fail(id: String, error: NwcPaymentError) {
        val record = records.value[id] ?: return
        if (record.status == PendingStatus.Succeeded) return
        val changed = transition(
            id = id,
            status = PendingStatus.Failed,
            error = error,
            guardsDynamicSource = false,
            visible = true
        )
        if (changed) {
            mutableEvents.tryEmit(
                PendingEvent.Failed(id, PaymentUiError.Nwc(error), record.visible)
            )
        }
    }

    private fun outcomeUnknown(id: String, detail: String?) {
        if (records.value[id]?.status == PendingStatus.Succeeded) return
        val error = NwcPaymentError.OutcomeUnknown(detail)
        transition(
            id = id,
            status = PendingStatus.OutcomeUnknown,
            error = error,
            visible = true
        )
        mutableEvents.tryEmit(PendingEvent.OutcomeUnknown(id))
    }

    private fun transition(
        id: String,
        status: PendingStatus,
        error: NwcPaymentError? = records.value[id]?.error,
        paidMsats: Long? = records.value[id]?.paidMsats,
        feeMsats: Long? = records.value[id]?.feeMsats,
        preimage: String? = records.value[id]?.preimage,
        guardsDynamicSource: Boolean = records.value[id]?.guardsDynamicSource ?: false,
        visible: Boolean = records.value[id]?.visible ?: false
    ): Boolean {
        val previous = records.value[id] ?: return false
        if (previous.status == PendingStatus.Succeeded && status != PendingStatus.Succeeded) {
            return false
        }
        val updated = previous.copy(
            status = status,
            error = error,
            paidMsats = paidMsats,
            feeMsats = feeMsats,
            preimage = preimage,
            guardsDynamicSource = guardsDynamicSource,
            visible = visible
        )
        if (updated == previous) return false
        records.update { it + (id to updated) }
        if (!status.isUnresolved()) {
            visibilityJobs.remove(id)?.cancel()
            reconciliationJobs.remove(id)?.cancel()
        }
        persistRecords()
        refreshDisplayItems()
        return true
    }

    private fun persistRecords() {
        store.save(records.value.values)
    }

    private fun recordsForDisplay(): List<PendingRecord> {
        val visibleRecords = records.value.values.filter(PendingRecord::visible)
        val unresolved = visibleRecords.filter { it.status.isUnresolved() }
        val resolved = visibleRecords.filterNot { it.status.isUnresolved() }
        val focused = resolved.firstOrNull { it.id == focusedRecordId }
        val recentResolved =
            buildList {
                focused?.let(::add)
                resolved
                    .asSequence()
                    .filterNot { it.id == focusedRecordId }
                    .sortedByDescending(PendingRecord::createdAtMs)
                    .take(MAX_RESOLVED_ATTEMPTS - size)
                    .forEach(::add)
            }
        return (unresolved + recentResolved).sortedByDescending(PendingRecord::createdAtMs)
    }

    private companion object {
        const val VISIBILITY_DELAY_MS = 5_000L
        const val LOOKUP_TIMEOUT_MS = 8_000L
        const val MAX_RESOLVED_ATTEMPTS = 10
        val LOOKUP_RETRY_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L, 15_000L)
    }
}

internal sealed interface PendingEvent {
    data class BecameVisible(val id: String) : PendingEvent

    data class Settled(
        val id: String,
        val paidMsats: Long,
        val feeMsats: Long,
        val preimage: String?,
        val wasVisible: Boolean
    ) : PendingEvent

    data class Failed(val id: String, val error: PaymentUiError, val wasVisible: Boolean) :
        PendingEvent

    data class OutcomeUnknown(val id: String) : PendingEvent
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
    val error: NwcPaymentError? = null,
    val paidMsats: Long? = null,
    val feeMsats: Long? = null,
    val visible: Boolean = false,
    val preimage: String? = null
)

private fun PendingStatus.isUnresolved(): Boolean = this == PendingStatus.Sending ||
    this == PendingStatus.Resolving ||
    this == PendingStatus.OutcomeUnknown

internal fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
