package xyz.lilsus.blip.feature.payment

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import xyz.lilsus.blip.integration.blink.BlinkFundingWallet
import xyz.lilsus.blip.integration.blink.BlinkPaymentAmount
import xyz.lilsus.blip.integration.blink.BlinkPaymentRequest
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.blip.integration.blink.BlinkWalletCurrency

internal class BlinkPaymentExecutionPhase(
    private val blinkWallet: BlinkWallet,
    scope: CoroutineScope,
    private val pendingTracker: PendingPaymentTracker,
    private val presentation: PaymentPresentationPhase,
    private val onResult: (PaymentExecutionResult) -> Unit
) {
    private val tasks = PaymentTaskRegistry(scope)
    val isSubmitting = tasks.active

    fun start(payment: ExecutablePayment) {
        presentation.showLoading()
        val amountMsats =
            payment.amountOverrideMsats ?: payment.invoice.amount?.msat ?: 0L
        val pendingId =
            pendingTracker.register(
                summary = payment.invoice,
                amountMsats = amountMsats,
                amountOverrideMsats = payment.amountOverrideMsats,
                fundingWallet = payment.fundingWallet,
                fundingAmountCents = payment.fundingAmountCents,
                origin = payment.origin,
                dynamicSourceKey = payment.dynamicSourceKey,
                replacesDynamicGuardId = payment.replacesDynamicGuardId
            )
        launch(
            attempt =
                PaymentExecutionAttempt(
                    pendingId = pendingId,
                    invoice = payment.invoice,
                    amountOverrideMsats = payment.amountOverrideMsats,
                    targetContext = payment.targetContext
                ),
            fundingWallet = payment.fundingWallet,
            fundingAmountCents = payment.fundingAmountCents
        )
    }

    fun retry(record: PendingRecord) {
        presentation.showLoading()
        pendingTracker.markSending(record.id)
        launch(
            attempt =
                PaymentExecutionAttempt(
                    pendingId = record.id,
                    invoice = record.summary,
                    amountOverrideMsats = record.amountOverrideMsats,
                    targetContext = null
                ),
            fundingWallet = record.fundingWallet,
            fundingAmountCents = record.fundingAmountCents
        )
    }

    fun reset() {
        tasks.reset()
    }

    private fun launch(
        attempt: PaymentExecutionAttempt,
        fundingWallet: BlinkFundingWallet,
        fundingAmountCents: Long?
    ) {
        tasks.launchReplacing(attempt.pendingId) { token ->
            val outcome =
                try {
                    PaymentExecutionOutcome.Provider(
                        blinkWallet.submitPayment(
                            BlinkPaymentRequest(
                                invoice = attempt.invoice.write(),
                                fundingWallet = fundingWallet,
                                amount =
                                    attempt.amountOverrideMsats?.let { amountMsats ->
                                        when (fundingWallet.currency) {
                                            BlinkWalletCurrency.BTC ->
                                                BlinkPaymentAmount.Bitcoin(amountMsats)

                                            BlinkWalletCurrency.USD ->
                                                BlinkPaymentAmount.Usd(
                                                    requireNotNull(fundingAmountCents)
                                                )
                                        }
                                    }
                            )
                        )
                    )
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    PaymentExecutionOutcome.StatusUnknown(cause.toPaymentUiError())
                }
            token.ensureCurrent()
            onResult(PaymentExecutionResult(attempt, outcome))
        }
    }
}
