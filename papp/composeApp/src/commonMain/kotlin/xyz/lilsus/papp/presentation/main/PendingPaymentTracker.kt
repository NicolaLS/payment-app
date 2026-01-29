package xyz.lilsus.papp.presentation.main

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
import xyz.lilsus.papp.data.exchange.currentTimeMillis
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceSummary
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.usecases.LookupPaymentUseCase
import xyz.lilsus.papp.presentation.main.PendingPaymentTracker.Companion.PENDING_NOTICE_DELAY_MS

/**
 * Tracks pending payments and their verification status.
 * Emits events when payments are settled or fail, allowing the ViewModel to update UI accordingly.
 *
 * Thread-safety: Uses MutableStateFlow with atomic updates for the records map.
 * Jobs are tracked separately and accessed only from coroutines launched on [scope].
 */
class PendingPaymentTracker(
    private val lookupPayment: LookupPaymentUseCase,
    private val currencyManager: CurrencyManager,
    private val scope: CoroutineScope
) {
    // Thread-safe record storage using StateFlow with atomic updates
    private val records = MutableStateFlow<Map<String, PendingRecord>>(emptyMap())

    // Job tracking - accessed only from scope's coroutines
    private val visibilityJobs = mutableMapOf<String, Job>()
    private val verificationJobs = mutableMapOf<String, Job>()
    private val requests = mutableMapOf<String, PayInvoiceRequest>()

    private val _displayItems = MutableStateFlow<List<PendingPaymentItem>>(emptyList())
    val displayItems: StateFlow<List<PendingPaymentItem>> = _displayItems.asStateFlow()

    private val _events = MutableSharedFlow<PendingEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PendingEvent> = _events.asSharedFlow()

    /**
     * Registers a new pending payment.
     * After [PENDING_NOTICE_DELAY_MS], if still waiting, emits [PendingEvent.BecameVisible].
     */
    fun register(
        summary: Bolt11InvoiceSummary,
        amountMsats: Long,
        origin: PendingOrigin,
        walletUri: String?,
        walletType: WalletType?
    ): String {
        val id = "pending-${currentTimeMillis()}-${records.value.size}"
        val record = PendingRecord(
            id = id,
            summary = summary,
            amountMsats = amountMsats,
            origin = origin,
            createdAtMs = currentTimeMillis(),
            walletUri = walletUri,
            walletType = walletType,
            paymentHash = summary.paymentHash
        )
        records.update { it + (id to record) }

        // After delay, if still waiting, show chip
        val visibilityJob = scope.launch {
            delay(PENDING_NOTICE_DELAY_MS)
            val current = records.value[id]
            if (current?.status == PendingStatus.Waiting) {
                records.update { records ->
                    records[id]?.let { records + (id to it.copy(visible = true)) } ?: records
                }
                refreshDisplayItems()
                _events.tryEmit(PendingEvent.BecameVisible(id))
            }
        }
        visibilityJobs[id] = visibilityJob

        return id
    }

    /**
     * Updates the status of a pending payment.
     */
    fun updateStatus(
        id: String,
        status: PendingStatus,
        error: AppError? = null,
        paidMsats: Long? = null,
        feeMsats: Long? = null
    ) {
        records.update { currentRecords ->
            currentRecords[id]?.let { record ->
                val updated = record.copy(
                    status = status,
                    error = error ?: record.error,
                    paidMsats = paidMsats ?: record.paidMsats,
                    feeMsats = feeMsats ?: record.feeMsats
                )
                currentRecords + (id to updated)
            } ?: currentRecords
        }
        if (status != PendingStatus.Waiting) {
            visibilityJobs.remove(id)?.cancel()
        }
        refreshDisplayItems()
    }

    /**
     * Marks a pending payment as successful.
     */
    fun markSuccess(id: String, paidMsats: Long, feeMsats: Long) {
        updateStatus(id, PendingStatus.Success, paidMsats = paidMsats, feeMsats = feeMsats)
    }

    /**
     * Marks a pending payment as failed.
     */
    fun markFailure(id: String, error: AppError) {
        updateStatus(id, PendingStatus.Failure, error = error)
    }

    /**
     * Makes a pending payment's chip visible immediately.
     */
    fun makeVisible(id: String) {
        records.update { records ->
            records[id]?.let { record ->
                if (!record.visible) {
                    records + (id to record.copy(visible = true))
                } else {
                    records
                }
            } ?: records
        }
        refreshDisplayItems()
    }

    /**
     * Gets a pending record by ID.
     */
    fun get(id: String): PendingRecord? = records.value[id]

    /**
     * Finds a pending record for a given invoice and wallet.
     */
    fun findByInvoiceAndWallet(paymentRequest: String, walletUri: String?): PendingRecord? =
        records.value.values.firstOrNull {
            it.summary.paymentRequest == paymentRequest && it.walletUri == walletUri
        }

    /**
     * Removes a pending record.
     */
    fun remove(id: String) {
        if (records.value[id] == null) return
        records.update { it - id }
        verificationJobs.remove(id)?.cancel()
        visibilityJobs.remove(id)?.cancel()
        requests.remove(id)?.cancel()
        refreshDisplayItems()
    }

    /**
     * Removes all pending records for the same invoice except the given one.
     * Called when one wallet successfully pays - the invoice can only be paid once.
     */
    fun removeOthersForSameInvoice(excludeId: String, paymentRequest: String) {
        val toRemove = records.value.values
            .filter { it.id != excludeId && it.summary.paymentRequest == paymentRequest }
            .map { it.id }
        toRemove.forEach { remove(it) }
    }

    /**
     * Sets the PayInvoiceRequest for a pending record.
     */
    fun setRequest(id: String, request: PayInvoiceRequest?) {
        if (records.value[id] != null) {
            if (request != null) {
                requests[id] = request
            } else {
                requests.remove(id)
            }
        }
    }

    /**
     * Clears the PayInvoiceRequest for a pending record if it matches.
     */
    fun clearRequestIfMatches(id: String, request: PayInvoiceRequest) {
        if (requests[id] === request) {
            requests.remove(id)
        }
    }

    /**
     * Starts background verification polling for a pending payment.
     * Emits [PendingEvent.Settled] or [PendingEvent.Failed] when resolved.
     */
    fun startVerification(
        id: String,
        summary: Bolt11InvoiceSummary,
        amountOverrideMsats: Long?,
        paymentHash: String
    ) {
        verificationJobs.remove(id)?.cancel()

        // Capture wallet context at verification start to ensure we look up on the correct wallet
        val record = records.value[id] ?: return
        val walletUri = record.walletUri
        val walletType = record.walletType

        val job = scope.launch {
            val startedAt = TimeSource.Monotonic.markNow()

            while (startedAt.elapsedNow() < VERIFICATION_TIMEOUT) {
                val currentRecord = records.value[id] ?: break
                if (currentRecord.status != PendingStatus.Waiting) break

                val remaining = VERIFICATION_TIMEOUT - startedAt.elapsedNow()
                if (remaining < MIN_LOOKUP_BUDGET) break

                val attemptStart = TimeSource.Monotonic.markNow()
                val result = withTimeoutOrNull(remaining) {
                    lookupPayment(paymentHash, walletUri, walletType)
                } ?: break

                when (result) {
                    is PaymentLookupResult.Settled -> {
                        val paidMsats = amountOverrideMsats ?: summary.amountMsats ?: 0L
                        val feeMsats = result.invoice.feesPaidMsats ?: 0L
                        markSuccess(id, paidMsats, feeMsats)
                        _events.tryEmit(
                            PendingEvent.Settled(id, result.invoice, paidMsats, feeMsats)
                        )
                        return@launch
                    }

                    PaymentLookupResult.Failed -> {
                        val error = AppError.PaymentRejected(
                            code = "LOOKUP_FAILED",
                            message = "Payment failed"
                        )
                        markFailure(id, error)
                        _events.tryEmit(PendingEvent.Failed(id, error))
                        return@launch
                    }

                    PaymentLookupResult.NotFound,
                    PaymentLookupResult.Pending,
                    is PaymentLookupResult.LookupError -> {
                        // Continue polling within the verification window.
                    }
                }

                val attemptDuration = attemptStart.elapsedNow()
                val remainingAfter = VERIFICATION_TIMEOUT - startedAt.elapsedNow()
                if (remainingAfter <= Duration.ZERO) break

                if (attemptDuration < FAST_RETRY_THRESHOLD) {
                    delay(minOf(RETRY_BACKOFF, remainingAfter))
                }
            }

            if (records.value[id]?.status == PendingStatus.Waiting) {
                val error = AppError.PaymentUnconfirmed(
                    paymentHash = paymentHash,
                    message = "Verification timed out"
                )
                markFailure(id, error)
                _events.tryEmit(PendingEvent.Failed(id, error))
            }
        }

        job.invokeOnCompletion { verificationJobs.remove(id) }
        verificationJobs[id] = job
    }

    /**
     * Refreshes display items using current currency state.
     */
    fun refreshDisplayItems() {
        val currencyState = currencyManager.state.value
        _displayItems.value = records.value.values
            .filter { it.visible }
            .map { record ->
                PendingPaymentItem(
                    id = record.id,
                    amount = currencyManager.convertMsatsToDisplay(
                        record.amountMsats,
                        currencyState
                    ),
                    status = record.status,
                    createdAtMs = record.createdAtMs,
                    fee = record.feeMsats?.let {
                        currencyManager.convertMsatsToDisplay(it, currencyState)
                    },
                    errorMessage = record.error?.let { errorMessageFor(it) }
                )
            }
    }

    /**
     * Clears all pending records and cancels all jobs.
     */
    fun clear() {
        verificationJobs.values.forEach { it.cancel() }
        verificationJobs.clear()
        visibilityJobs.values.forEach { it.cancel() }
        visibilityJobs.clear()
        requests.values.forEach { it.cancel() }
        requests.clear()
        records.value = emptyMap()
        _displayItems.value = emptyList()
    }

    private fun errorMessageFor(error: AppError): String = when (error) {
        is AppError.PaymentRejected -> error.message ?: error.code ?: "Rejected"
        AppError.NetworkUnavailable -> "No internet"
        is AppError.RelayConnectionFailed -> "Connection failed"
        AppError.Timeout -> "Timed out"
        is AppError.PaymentUnconfirmed -> error.message ?: "Unconfirmed"
        is AppError.Unexpected -> error.message ?: "Error"
        else -> "Error"
    }

    companion object {
        private const val PENDING_NOTICE_DELAY_MS = 5_000L
        private val VERIFICATION_TIMEOUT = 30.seconds
        private val MIN_LOOKUP_BUDGET = 2.seconds
        private val FAST_RETRY_THRESHOLD = 750.milliseconds
        private val RETRY_BACKOFF = 500.milliseconds
    }
}

/**
 * Events emitted by PendingPaymentTracker.
 */
sealed class PendingEvent {
    /** A pending chip became visible after the delay. */
    data class BecameVisible(val id: String) : PendingEvent()

    /** A pending payment was verified as settled. */
    data class Settled(
        val id: String,
        val invoice: PaidInvoice,
        val paidMsats: Long,
        val feeMsats: Long
    ) : PendingEvent()

    /** A pending payment was verified as failed. */
    data class Failed(val id: String, val error: AppError) : PendingEvent()
}

/**
 * Origin of a pending payment request.
 */
enum class PendingOrigin {
    Invoice,
    ManualEntry,
    LnurlFixed,
    LnurlManual
}

/**
 * Immutable record of a pending payment.
 * Thread-safe: stored in MutableStateFlow with copy-on-write updates.
 */
data class PendingRecord(
    val id: String,
    val summary: Bolt11InvoiceSummary,
    val amountMsats: Long,
    val origin: PendingOrigin,
    val createdAtMs: Long,
    val walletUri: String?,
    val walletType: WalletType?,
    val paymentHash: String?,
    val status: PendingStatus = PendingStatus.Waiting,
    val error: AppError? = null,
    val paidMsats: Long? = null,
    val feeMsats: Long? = null,
    val visible: Boolean = false
)
