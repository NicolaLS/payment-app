package xyz.lilsus.blip.feature.payment

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.lilsus.raylsuite.feature.paymentcurrency.CurrencyState

internal class PaymentSessionState(val preparation: PaymentPreparation) {
    val uiState = MutableStateFlow<PaymentUiState>(PaymentUiState.Active)
    val transactionDetailNavigationTarget = MutableStateFlow<String?>(null)
    val newSessionTransactionCount = MutableStateFlow(0)

    var pendingRetry: PendingRetryChoice? = null
    var lastPaymentResult: CompletedPayment? = null
    private val knownTransactionIds = mutableSetOf<String>()
    private val newTransactionIds = mutableSetOf<String>()
    val paymentJobs = mutableMapOf<String, Job>()
    var paymentAdmissionInProgress = false

    fun onSessionTransactionsOpened(currentTransactionIds: Iterable<String>) {
        newTransactionIds.clear()
        knownTransactionIds += currentTransactionIds
        newSessionTransactionCount.value = 0
    }

    fun updateSessionTransactionIds(currentTransactionIds: Set<String>) {
        knownTransactionIds.retainAll(currentTransactionIds)
        newTransactionIds.retainAll(currentTransactionIds)
        val unseenIds = currentTransactionIds.filterNot(knownTransactionIds::contains)
        if (unseenIds.isNotEmpty()) {
            knownTransactionIds += unseenIds
            newTransactionIds += unseenIds
        }
        newSessionTransactionCount.value = newTransactionIds.size
    }

    fun markTransactionSeen(id: String) {
        knownTransactionIds += id
        newTransactionIds -= id
        newSessionTransactionCount.value = newTransactionIds.size
    }

    fun requestTransactionDetailNavigation(id: String) {
        markTransactionSeen(id)
        uiState.value = PaymentUiState.Active
        showTransactionDetail(id)
    }

    fun showTransactionDetail(id: String) {
        transactionDetailNavigationTarget.value = id
    }

    fun onTransactionDetailNavigationHandled(id: String) {
        if (transactionDetailNavigationTarget.value == id) {
            transactionDetailNavigationTarget.value = null
        }
    }

    fun dismissResult() {
        transactionDetailNavigationTarget.value = null
        uiState.value = PaymentUiState.Active
    }

    fun reset(currencyState: CurrencyState) {
        paymentAdmissionInProgress = false
        paymentJobs.values.toList().forEach(Job::cancel)
        paymentJobs.clear()
        preparation.reset(currencyState)
        pendingRetry = null
        lastPaymentResult = null
        knownTransactionIds.clear()
        newTransactionIds.clear()
        newSessionTransactionCount.value = 0
        transactionDetailNavigationTarget.value = null
        uiState.value = PaymentUiState.Active
    }
}

internal data class PendingRetryChoice(
    val recordId: String,
    val continuation: PendingRetryContinuation
)

internal data class CompletedPayment(
    val amountMsats: Long,
    val feeMsats: Long,
    val showEstimatedFeeHint: Boolean,
    val wasAlreadyPaid: Boolean,
    val preimage: String?
)
