package xyz.lilsus.raylsuite.feature.payment

import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
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
import kotlinx.coroutines.withTimeoutOrNull
import xyz.lilsus.raylsuite.core.payment.PaidInvoice
import xyz.lilsus.raylsuite.core.payment.PaymentError
import xyz.lilsus.raylsuite.core.payment.PaymentHash
import xyz.lilsus.raylsuite.core.payment.PaymentLookupResult
import xyz.lilsus.raylsuite.core.payment.PaymentProvider

internal class PendingPaymentTracker(
    private val paymentProvider: PaymentProvider,
    private val currencyManager: PaymentCurrencyManager,
    private val scope: CoroutineScope,
    private val showEstimatedFeeHint: Boolean,
    private val clock: () -> Long = ::platformCurrentTimeMillis
) {
    private val records = MutableStateFlow<Map<String, PendingRecord>>(emptyMap())
    private val visibilityJobs = mutableMapOf<String, Job>()
    private val verificationJobs = mutableMapOf<String, Job>()

    private val mutableDisplayItems = MutableStateFlow<List<SessionTransactionItem>>(emptyList())
    val displayItems: StateFlow<List<SessionTransactionItem>> = mutableDisplayItems.asStateFlow()

    private val mutableEvents = MutableSharedFlow<PendingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PendingEvent> = mutableEvents.asSharedFlow()

    fun register(
        summary: Bolt11Invoice,
        amountMsats: Long,
        origin: PendingOrigin,
        dynamicSourceKey: String? = null
    ): String {
        val id = "pending-${clock()}-${records.value.size}"
        val record =
            PendingRecord(
                id = id,
                summary = summary,
                amountMsats = amountMsats,
                origin = origin,
                createdAtMs = clock(),
                dynamicSourceKey = dynamicSourceKey,
                paymentHash = PaymentHash(summary.paymentHash.toHex())
            )
        records.update { it + (id to record) }
        refreshDisplayItems()

        visibilityJobs[id] =
            scope.launch {
                delay(PENDING_NOTICE_DELAY_MS)
                val current = records.value[id]
                if (current?.status == PendingStatus.Waiting) {
                    records.update { all ->
                        all[id]?.let { all + (id to it.copy(visible = true)) } ?: all
                    }
                    refreshDisplayItems()
                    mutableEvents.tryEmit(PendingEvent.BecameVisible(id))
                }
            }
        return id
    }

    fun get(id: String): PendingRecord? = records.value[id]

    fun findWaitingByPaymentRequest(paymentRequest: String): PendingRecord? =
        records.value.values.firstOrNull { record ->
            record.status == PendingStatus.Waiting &&
                record.summary.write() == paymentRequest
        }

    fun findWaitingByDynamicSourceKey(dynamicSourceKey: String): PendingRecord? =
        records.value.values.firstOrNull { record ->
            record.status == PendingStatus.Waiting &&
                record.dynamicSourceKey == dynamicSourceKey
        }

    fun markSuccess(
        id: String,
        paidMsats: Long,
        feeMsats: Long,
        wasAlreadyPaid: Boolean = false,
        preimage: String? = null
    ) {
        updateStatus(
            id = id,
            status = PendingStatus.Success,
            paidMsats = paidMsats,
            feeMsats = feeMsats,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimage
        )
    }

    fun markFailure(id: String, error: PaymentUiError) {
        updateStatus(id = id, status = PendingStatus.Failure, error = error)
    }

    fun makeVisible(id: String) {
        records.update { all ->
            all[id]?.let { record ->
                if (record.visible) all else all + (id to record.copy(visible = true))
            } ?: all
        }
        refreshDisplayItems()
    }

    fun startVerification(
        id: String,
        summary: Bolt11Invoice,
        amountOverrideMsats: Long?,
        paymentHash: PaymentHash
    ) {
        verificationJobs.remove(id)?.cancel()
        if (records.value[id] == null) return

        val job =
            scope.launch {
                val startedAt = TimeSource.Monotonic.markNow()
                while (startedAt.elapsedNow() < VERIFICATION_TIMEOUT) {
                    val currentRecord = records.value[id] ?: break
                    if (currentRecord.status != PendingStatus.Waiting) break

                    val remaining = VERIFICATION_TIMEOUT - startedAt.elapsedNow()
                    if (remaining < MIN_LOOKUP_BUDGET) break

                    val attemptStart = TimeSource.Monotonic.markNow()
                    val result =
                        withTimeoutOrNull(remaining) {
                            paymentProvider.lookupPayment(paymentHash)
                        } ?: break

                    when (result) {
                        is PaymentLookupResult.Settled -> {
                            settleVerifiedPayment(id, summary, amountOverrideMsats, result.invoice)
                            return@launch
                        }

                        PaymentLookupResult.Failed -> {
                            val error =
                                PaymentUiError.Provider(
                                    PaymentError.PaymentRejected(
                                        code = "LOOKUP_FAILED",
                                        detail = "Payment failed"
                                    )
                                )
                            markFailure(id, error)
                            mutableEvents.tryEmit(PendingEvent.Failed(id, error))
                            return@launch
                        }

                        PaymentLookupResult.NotFound,
                        PaymentLookupResult.Pending -> Unit

                        is PaymentLookupResult.LookupError -> {
                            if (!result.error.isRetryableLookupError()) {
                                val error = PaymentUiError.Provider(result.error)
                                markFailure(id, error)
                                mutableEvents.tryEmit(PendingEvent.Failed(id, error))
                                return@launch
                            }
                        }
                    }

                    val remainingAfter = VERIFICATION_TIMEOUT - startedAt.elapsedNow()
                    if (remainingAfter <= Duration.ZERO) break
                    if (attemptStart.elapsedNow() < FAST_RETRY_THRESHOLD) {
                        delay(minOf(RETRY_BACKOFF, remainingAfter))
                    }
                }

                if (records.value[id]?.status == PendingStatus.Waiting) {
                    val error =
                        PaymentUiError.Provider(
                            PaymentError.PaymentUnconfirmed(
                                paymentHash = paymentHash,
                                detail = "Verification timed out"
                            )
                        )
                    markFailure(id, error)
                    mutableEvents.tryEmit(PendingEvent.Failed(id, error))
                }
            }
        job.invokeOnCompletion { verificationJobs.remove(id) }
        verificationJobs[id] = job
    }

    fun refreshDisplayItems() {
        val currencyState = currencyManager.state.value
        mutableDisplayItems.value =
            records.value.values
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

    fun clear() {
        val jobs = visibilityJobs.values + verificationJobs.values
        visibilityJobs.clear()
        verificationJobs.clear()
        jobs.forEach(Job::cancel)
        records.value = emptyMap()
        mutableDisplayItems.value = emptyList()
    }

    private fun updateStatus(
        id: String,
        status: PendingStatus,
        error: PaymentUiError? = null,
        paidMsats: Long? = null,
        feeMsats: Long? = null,
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
                                error = error ?: record.error,
                                paidMsats = paidMsats ?: record.paidMsats,
                                feeMsats = feeMsats ?: record.feeMsats,
                                wasAlreadyPaid = wasAlreadyPaid ?: record.wasAlreadyPaid,
                                preimage = preimage ?: record.preimage
                            )
                        )
            } ?: all
        }
        if (status != PendingStatus.Waiting) {
            visibilityJobs.remove(id)?.cancel()
        }
        refreshDisplayItems()
    }

    private fun settleVerifiedPayment(
        id: String,
        summary: Bolt11Invoice,
        amountOverrideMsats: Long?,
        invoice: PaidInvoice
    ) {
        val paidMsats =
            if (invoice.wasAlreadyPaid) {
                0L
            } else {
                amountOverrideMsats ?: summary.amount?.msat ?: 0L
            }
        val feeMsats = if (invoice.wasAlreadyPaid) 0L else invoice.feesPaidMsats ?: 0L
        markSuccess(
            id = id,
            paidMsats = paidMsats,
            feeMsats = feeMsats,
            wasAlreadyPaid = invoice.wasAlreadyPaid,
            preimage = invoice.preimageHex
        )
        mutableEvents.tryEmit(
            PendingEvent.Settled(
                id = id,
                invoice = invoice,
                paidMsats = paidMsats,
                feeMsats = feeMsats
            )
        )
    }

    private fun PaymentUiError.shortMessage(): String? = when (this) {
        is PaymentUiError.Provider ->
            when (val providerError = error) {
                is PaymentError.PaymentRejected ->
                    providerError.detail ?: providerError.code

                is PaymentError.PaymentUnconfirmed -> providerError.detail
                is PaymentError.WalletConnectionFailed -> providerError.detail
                is PaymentError.AuthenticationFailure -> providerError.detail
                is PaymentError.InsufficientPermissions -> providerError.detail
                is PaymentError.Unexpected -> providerError.detail
                else -> null
            }

        is PaymentUiError.InvalidInvoice -> reason
        is PaymentUiError.Lnurl -> reason
        is PaymentUiError.Unexpected -> detail
    }

    private fun PaymentError.isRetryableLookupError(): Boolean = when (this) {
        PaymentError.NetworkUnavailable,
        PaymentError.Timeout,
        is PaymentError.WalletConnectionFailed -> true

        else -> false
    }

    private companion object {
        const val PENDING_NOTICE_DELAY_MS = 5_000L
        val VERIFICATION_TIMEOUT = 30.seconds
        val MIN_LOOKUP_BUDGET = 2.seconds
        val FAST_RETRY_THRESHOLD = 750.milliseconds
        val RETRY_BACKOFF = 500.milliseconds
    }
}

internal sealed interface PendingEvent {
    data class BecameVisible(val id: String) : PendingEvent

    data class Settled(
        val id: String,
        val invoice: PaidInvoice,
        val paidMsats: Long,
        val feeMsats: Long
    ) : PendingEvent

    data class Failed(val id: String, val error: PaymentUiError) : PendingEvent
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
    val origin: PendingOrigin,
    val createdAtMs: Long,
    val dynamicSourceKey: String?,
    val paymentHash: PaymentHash,
    val status: PendingStatus = PendingStatus.Waiting,
    val error: PaymentUiError? = null,
    val paidMsats: Long? = null,
    val feeMsats: Long? = null,
    val visible: Boolean = false,
    val wasAlreadyPaid: Boolean = false,
    val preimage: String? = null
)

internal expect fun platformCurrentTimeMillis(): Long
