package xyz.lilsus.blip.feature.payment

import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.utils.msat
import xyz.lilsus.blip.integration.blink.BlinkApiError
import xyz.lilsus.blip.integration.blink.BlinkPaymentOutcome
import xyz.lilsus.blip.integration.blink.BlinkWalletCurrency
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

    fun handle(result: PaymentExecutionResult) {
        if (pendingTracker.get(result.attempt.pendingId) == null) return
        when (val outcome = result.outcome) {
            is PaymentExecutionOutcome.StatusUnknown ->
                handlePaymentStatusUnknown(
                    pendingId = result.attempt.pendingId,
                    error = outcome.error
                )

            is PaymentExecutionOutcome.Provider ->
                when (val providerOutcome = outcome.outcome) {
                    is BlinkPaymentOutcome.Paid ->
                        handlePaymentSuccess(
                            attempt = result.attempt,
                            feesPaidMsats = providerOutcome.feesPaidMsats,
                            preimageHex = providerOutcome.preimageHex,
                            wasAlreadyPaid = false
                        )

                    BlinkPaymentOutcome.AlreadyPaid ->
                        handlePaymentSuccess(
                            attempt = result.attempt,
                            feesPaidMsats = null,
                            preimageHex = null,
                            wasAlreadyPaid = true
                        )

                    BlinkPaymentOutcome.Pending ->
                        handlePaymentPendingInBlink(result.attempt.pendingId)

                    is BlinkPaymentOutcome.DefinitiveFailure ->
                        handlePaymentFailure(
                            pendingId = result.attempt.pendingId,
                            error = PaymentUiError.Blink(providerOutcome.error)
                        )

                    is BlinkPaymentOutcome.StatusUnknown ->
                        handlePaymentStatusUnknown(
                            pendingId = result.attempt.pendingId,
                            error = PaymentUiError.Blink(providerOutcome.error)
                        )
                }
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
                    it.status == PendingStatus.StatusUnknown ||
                        it.status == PendingStatus.Failure
                }
                ?: return null
        if (rejectExpiredInvoice(record.summary)) return null
        if (
            record.amountOverrideMsats != null &&
            record.fundingWallet.currency == BlinkWalletCurrency.USD &&
            record.fundingAmountCents == null
        ) {
            presentation.presentError(
                PaymentUiError.Blink(BlinkApiError.FundingWalletUnavailable)
            )
            return null
        }
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
        if (event is PendingEvent.BecameVisible) {
            presentation.showActiveIfLoading()
        }
    }

    fun reset() {
        retrySession.reset()
    }

    private fun handlePaymentSuccess(
        attempt: PaymentExecutionAttempt,
        feesPaidMsats: Long?,
        preimageHex: String?,
        wasAlreadyPaid: Boolean
    ) {
        val record = pendingTracker.get(attempt.pendingId) ?: return
        val showDirectResult =
            presentation.shouldShowDirectPaymentResult(record.visible)
        if (showDirectResult) presentation.markTransactionSeen(attempt.pendingId)
        val paidMsats =
            if (wasAlreadyPaid) {
                0L
            } else {
                attempt.amountOverrideMsats ?: attempt.invoice.amount?.msat ?: 0L
            }
        val feeMsats =
            if (wasAlreadyPaid) {
                0L
            } else {
                feesPaidMsats ?: 0L
            }
        pendingTracker.markSuccess(
            id = attempt.pendingId,
            paidMsats = paidMsats,
            feeMsats = feeMsats,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimageHex
        )
        if (!wasAlreadyPaid) {
            reportPaymentSuccess(attempt.targetContext, paidMsats)
        }
        if (vibrateOnPayment()) haptics.notifyPaymentSuccess()
        if (!showDirectResult) return

        presentation.showPaymentSuccess(
            CompletedPayment(
                amountMsats = paidMsats,
                feeMsats = feeMsats,
                showEstimatedFeeHint = showEstimatedFeeHint && !wasAlreadyPaid,
                wasAlreadyPaid = wasAlreadyPaid,
                preimage = preimageHex
            )
        )
    }

    private fun handlePaymentFailure(pendingId: String, error: PaymentUiError) {
        val record = pendingTracker.get(pendingId) ?: return
        val showDirectResult =
            presentation.shouldShowDirectPaymentResult(record.visible)
        val clarificationOpen = retrySession.recordId == pendingId
        if (showDirectResult || clarificationOpen) {
            presentation.markTransactionSeen(pendingId)
        }
        pendingTracker.markFailure(pendingId, error)
        if (clarificationOpen) retrySession.clearIf(pendingId)
        if (!showDirectResult && !clarificationOpen) return
        presentation.showPaymentError(error, emitEvent = true)
    }

    private fun handlePaymentPendingInBlink(pendingId: String) {
        val record = pendingTracker.get(pendingId) ?: return
        val showDirectResult =
            presentation.shouldShowDirectPaymentResult(record.visible)
        if (showDirectResult) presentation.markTransactionSeen(pendingId)
        pendingTracker.markPendingInBlink(pendingId)
        presentation.showActiveIfLoading()
        if (showDirectResult) {
            presentation.showTransactionDetail(pendingId)
        }
    }

    private fun handlePaymentStatusUnknown(pendingId: String, error: PaymentUiError) {
        val record = pendingTracker.get(pendingId) ?: return
        val showDirectResult =
            presentation.shouldShowDirectPaymentResult(record.visible)
        if (showDirectResult) presentation.markTransactionSeen(pendingId)
        pendingTracker.markStatusUnknown(pendingId, error)
        presentation.showActiveIfLoading()
        if (showDirectResult) {
            presentation.showTransactionDetail(pendingId)
        }
    }

    private fun reportPaymentSuccess(context: HubTargetContext?, paidMsats: Long) {
        context ?: return
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
