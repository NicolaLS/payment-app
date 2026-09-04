package xyz.lilsus.lasr.feature.payment

import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.currentTimestampSeconds
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey
import xyz.lilsus.raylsuite.core.ui.platform.HapticFeedbackManager
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentAmountQuote
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController

internal class PaymentReconciliationPhase(
    private val pendingTracker: PendingPaymentTracker,
    private val presentation: PaymentPresentationPhase,
    private val paymentHub: PaymentHubController,
    private val haptics: HapticFeedbackManager,
    private val showEstimatedFeeHint: Boolean,
    private val vibrateOnPayment: () -> Boolean,
    private val offerToSaveNewTargets: () -> Boolean
) {
    private val retrySession = PaymentRetrySession()
    private val hubContexts = mutableMapOf<String, HubTargetContext>()

    fun registerAttempt(attempt: PaymentExecutionAttempt) {
        attempt.targetContext?.let { context ->
            hubContexts[attempt.pendingId] = context
        }
    }

    fun showPendingRetryPrompt(record: PendingRecord, continuation: PendingRetryContinuation) {
        pendingTracker.focus(record.id)
        retrySession.show(PendingRetryChoice(record.id, continuation))
        presentation.showPendingRetry(record.id)
    }

    fun takeNewInvoiceChoice(): PendingRetryChoice? = retrySession.take()

    fun takePendingRetryRecord(): PendingRecord? {
        val id = retrySession.take()?.recordId ?: return null
        return retryRecord(id)
    }

    fun retryRecord(id: String): PendingRecord? {
        val record =
            pendingTracker.get(id)
                ?.takeIf {
                    it.status == PendingStatus.OutcomeUnknown ||
                        it.status == PendingStatus.Failed
                }
                ?: return null
        if (rejectExpiredInvoice(record.summary)) return null
        return record
    }

    fun viewPendingPayment() {
        val id = retrySession.take()?.recordId ?: return
        requestTransactionDetailNavigation(id)
    }

    fun dismissPendingRetry() {
        if (retrySession.take() != null) {
            presentation.showActive()
        }
    }

    fun sessionTransactionsOpened() {
        presentation.onSessionTransactionsOpened(
            pendingTracker.displayItems.value.map(SessionTransactionItem::id)
        )
    }

    fun requestTransactionDetailNavigation(id: String) {
        if (pendingTracker.get(id) == null) return
        pendingTracker.focus(id)
        presentation.requestTransactionDetailNavigation(id)
    }

    fun handlePendingEvent(event: PendingEvent) {
        when (event) {
            is PendingEvent.BecameVisible ->
                presentation.showActiveIfLoading()

            is PendingEvent.Settled -> {
                reportPaymentSuccess(event.id, event.paidMsats)
                if (vibrateOnPayment()) haptics.notifyPaymentSuccess()
                if (
                    !event.wasVisible &&
                    presentation.uiState.value is PaymentUiState.Loading
                ) {
                    presentation.showPaymentSuccess(
                        CompletedPayment(
                            amountMsats = event.paidMsats,
                            feeMsats = event.feeMsats,
                            showEstimatedFeeHint = showEstimatedFeeHint,
                            wasAlreadyPaid = false,
                            preimage = event.preimage
                        )
                    )
                    presentation.showTransactionDetail(event.id)
                }
            }

            is PendingEvent.Failed -> {
                hubContexts.remove(event.id)
                if (retrySession.recordId == event.id) {
                    retrySession.clearIf(event.id)
                    presentation.showActive()
                }
                if (
                    !event.wasVisible &&
                    presentation.uiState.value is PaymentUiState.Loading
                ) {
                    presentation.showPaymentError(event.error, emitEvent = true)
                    presentation.showTransactionDetail(event.id)
                }
            }

            is PendingEvent.OutcomeUnknown ->
                hubContexts.remove(event.id)
        }
    }

    fun reset() {
        retrySession.reset()
        hubContexts.clear()
    }

    private fun reportPaymentSuccess(pendingId: String, paidMsats: Long) {
        val context = hubContexts.remove(pendingId) ?: return
        if (paidMsats <= 0L) return
        val targetId = context.targetId
        if (targetId != null) {
            paymentHub.recordSuccessfulPayment(targetId)
        } else if (offerToSaveNewTargets()) {
            paymentHub.offerSave(context.address)
        }
    }

    private fun rejectExpiredInvoice(invoice: Bolt11Invoice): Boolean {
        if (!invoice.isExpired(currentTimestampSeconds())) return false
        presentation.presentError(PaymentUiError.InvalidInvoice("Invoice has expired"))
        return true
    }
}

internal sealed interface PaymentRetryState {
    data object Idle : PaymentRetryState

    data class Prompt(val choice: PendingRetryChoice) : PaymentRetryState
}

internal class PaymentRetrySession {
    var state: PaymentRetryState = PaymentRetryState.Idle
        private set

    val recordId: String?
        get() = (state as? PaymentRetryState.Prompt)?.choice?.recordId

    fun show(choice: PendingRetryChoice) {
        state = PaymentRetryState.Prompt(choice)
    }

    fun take(): PendingRetryChoice? {
        val choice = (state as? PaymentRetryState.Prompt)?.choice ?: return null
        state = PaymentRetryState.Idle
        return choice
    }

    fun clearIf(recordId: String) {
        if (this.recordId == recordId) {
            state = PaymentRetryState.Idle
        }
    }

    fun reset() {
        state = PaymentRetryState.Idle
    }
}

internal data class PendingRetryChoice(
    val recordId: String,
    val continuation: PendingRetryContinuation
)

internal sealed interface PendingRetryContinuation {
    data class Lnurl(
        val endpoint: String,
        val sourceKey: DynamicPaymentSourceKey,
        val paymentSource: PaymentRequestSource
    ) : PendingRetryContinuation

    data class LightningAddress(
        val address: xyz.lilsus.raylsuite.core.model.LightningAddress,
        val sourceKey: DynamicPaymentSourceKey,
        val paymentSource: PaymentRequestSource,
        val targetContext: HubTargetContext? = null,
        val presetQuote: PaymentAmountQuote? = null,
        val targetComment: String? = null
    ) : PendingRetryContinuation
}
