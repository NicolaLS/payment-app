package xyz.lilsus.lasr.feature.payment

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import xyz.lilsus.lasr.integration.nwc.NwcPayOutcome
import xyz.lilsus.lasr.integration.nwc.NwcWallet

internal class NwcPaymentExecutionPhase(
    private val nwcWallet: NwcWallet,
    scope: CoroutineScope,
    private val pendingTracker: PendingPaymentTracker,
    private val presentation: PaymentPresentationPhase,
    private val onAttemptRegistered: (PaymentExecutionAttempt) -> Unit
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
                origin = payment.origin,
                dynamicSourceKey = payment.dynamicSourceKey,
                replacesDynamicGuardId = payment.replacesDynamicGuardId
            )
        val attempt =
            PaymentExecutionAttempt(
                pendingId = pendingId,
                invoice = payment.invoice,
                amountOverrideMsats = payment.amountOverrideMsats,
                targetContext = payment.targetContext
            )
        onAttemptRegistered(attempt)
        launch(attempt)
    }

    fun retry(record: PendingRecord) {
        val retryRecord = pendingTracker.retry(record.id) ?: return
        presentation.showLoading()
        launch(
            PaymentExecutionAttempt(
                pendingId = retryRecord.id,
                invoice = retryRecord.summary,
                amountOverrideMsats = retryRecord.amountOverrideMsats,
                targetContext = null
            )
        )
    }

    fun reset() {
        tasks.reset()
    }

    private fun launch(attempt: PaymentExecutionAttempt) {
        tasks.launchReplacing(attempt.pendingId) { token ->
            val outcome =
                try {
                    nwcWallet.payInvoice(
                        invoice = attempt.invoice.write(),
                        amountMsats = attempt.amountOverrideMsats,
                        timeoutMs = PAY_RESPONSE_TIMEOUT_MS
                    )
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Throwable) {
                    NwcPayOutcome.Uncertain(cause.message)
                }
            token.ensureCurrent()
            pendingTracker.applyPayOutcome(attempt.pendingId, outcome)
        }
    }

    private companion object {
        const val PAY_RESPONSE_TIMEOUT_MS = 15_000L
    }
}
