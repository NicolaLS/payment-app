package xyz.lilsus.papp.presentation.main

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.papp.data.exchange.currentTimeMillis
import xyz.lilsus.papp.domain.bolt11.Bolt11InvoiceSummary
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.usecases.LookupPaymentUseCase

/**
 * Tracks pending payments and their verification status.
 * Emits events when payments are settled or fail, allowing the ViewModel to update UI accordingly.
 */
class PendingPaymentTracker(
    private val lookupPayment: LookupPaymentUseCase,
    private val currencyManager: CurrencyManager,
    private val scope: CoroutineScope
) {
    private val pendingRequests = LinkedHashMap<String, PendingRecord>()
    private val pendingVerificationJobs = mutableMapOf<String, Job>()

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
        val id = "pending-${currentTimeMillis()}-${pendingRequests.size}"
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
        pendingRequests[id] = record

        // After delay, if still waiting, show chip
        record.visibilityJob = scope.launch {
            delay(PENDING_NOTICE_DELAY_MS)
            if (pendingRequests[id]?.status == PendingStatus.Waiting) {
                record.visible = true
                refreshDisplayItems()
                _events.tryEmit(PendingEvent.BecameVisible(id))
            }
        }
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
        val record = pendingRequests[id] ?: return
        record.status = status
        record.error = error ?: record.error
        paidMsats?.let { record.paidMsats = it }
        feeMsats?.let { record.feeMsats = it }
        if (status != PendingStatus.Waiting) {
            record.visibilityJob?.cancel()
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
        val record = pendingRequests[id] ?: return
        if (!record.visible) {
            record.visible = true
            refreshDisplayItems()
        }
    }

    /**
     * Gets a pending record by ID.
     */
    fun get(id: String): PendingRecord? = pendingRequests[id]

    /**
     * Finds a pending record for a given invoice and wallet.
     */
    fun findByInvoiceAndWallet(paymentRequest: String, walletUri: String?): PendingRecord? =
        pendingRequests.values.firstOrNull {
            it.summary.paymentRequest == paymentRequest && it.walletUri == walletUri
        }

    /**
     * Removes a pending record.
     */
    fun remove(id: String) {
        val record = pendingRequests.remove(id) ?: return
        pendingVerificationJobs.remove(id)?.cancel()
        record.visibilityJob?.cancel()
        record.request?.cancel()
        refreshDisplayItems()
    }

    /**
     * Removes all pending records for the same invoice except the given one.
     * Called when one wallet successfully pays - the invoice can only be paid once.
     */
    fun removeOthersForSameInvoice(excludeId: String, paymentRequest: String) {
        val toRemove = pendingRequests.values
            .filter { it.id != excludeId && it.summary.paymentRequest == paymentRequest }
            .map { it.id }
        toRemove.forEach { remove(it) }
    }

    /**
     * Sets the PayInvoiceRequest for a pending record.
     */
    fun setRequest(id: String, request: PayInvoiceRequest?) {
        pendingRequests[id]?.request = request
    }

    /**
     * Clears the PayInvoiceRequest for a pending record if it matches.
     */
    fun clearRequestIfMatches(id: String, request: PayInvoiceRequest) {
        val record = pendingRequests[id]
        if (record?.request === request) {
            record.request = null
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
        pendingVerificationJobs[id]?.cancel()

        val job = scope.launch {
            var attempt = 0
            while (attempt < MAX_VERIFICATION_ATTEMPTS) {
                delay(VERIFICATION_INTERVAL_MS)

                val record = pendingRequests[id] ?: break
                if (record.status != PendingStatus.Waiting) break

                attempt++

                when (val result = lookupPayment(paymentHash)) {
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
                        // Continue polling
                    }
                }
            }
            // Max attempts reached - keep as pending
        }

        job.invokeOnCompletion { pendingVerificationJobs.remove(id) }
        pendingVerificationJobs[id] = job
    }

    /**
     * Refreshes display items using current currency state.
     */
    fun refreshDisplayItems() {
        val currencyState = currencyManager.state.value
        _displayItems.value = pendingRequests.values
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
        pendingVerificationJobs.values.forEach { it.cancel() }
        pendingVerificationJobs.clear()
        pendingRequests.values.forEach {
            it.request?.cancel()
            it.visibilityJob?.cancel()
        }
        pendingRequests.clear()
        _displayItems.value = emptyList()
    }

    private fun errorMessageFor(error: AppError): String = when (error) {
        is AppError.PaymentRejected -> error.message ?: error.code ?: "Rejected"
        AppError.NetworkUnavailable -> "Network error"
        AppError.Timeout -> "Timed out"
        is AppError.PaymentUnconfirmed -> error.message ?: "Unconfirmed"
        is AppError.Unexpected -> error.message ?: "Error"
        else -> "Error"
    }

    companion object {
        private const val PENDING_NOTICE_DELAY_MS = 5_000L
        private const val VERIFICATION_INTERVAL_MS = 1_000L
        private const val MAX_VERIFICATION_ATTEMPTS = 30
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
 * Internal record of a pending payment.
 */
class PendingRecord(
    val id: String,
    val summary: Bolt11InvoiceSummary,
    val amountMsats: Long,
    val origin: PendingOrigin,
    val createdAtMs: Long,
    val walletUri: String?,
    val walletType: WalletType?,
    val paymentHash: String?,
    var status: PendingStatus = PendingStatus.Waiting,
    var error: AppError? = null,
    var paidMsats: Long? = null,
    var feeMsats: Long? = null,
    var visible: Boolean = false,
    var request: PayInvoiceRequest? = null,
    internal var visibilityJob: Job? = null
)
