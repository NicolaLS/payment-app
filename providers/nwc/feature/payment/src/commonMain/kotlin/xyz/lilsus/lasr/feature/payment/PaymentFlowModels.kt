package xyz.lilsus.lasr.feature.payment

import fr.acinq.lightning.payment.Bolt11Invoice
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentAmountQuote

internal data class PreparedPayment(
    val invoice: Bolt11Invoice,
    val amountOverrideMsats: Long?,
    val origin: PendingOrigin,
    val source: PaymentRequestSource,
    val dynamicSourceKey: DynamicPaymentSourceKey? = null,
    val targetContext: HubTargetContext? = null,
    val replacesDynamicGuardId: String? = null,
    val lnurlAuthorized: Boolean = false,
    val paymentQuote: PaymentAmountQuote? = null
)

internal data class ExecutablePayment(
    val invoice: Bolt11Invoice,
    val amountOverrideMsats: Long?,
    val origin: PendingOrigin,
    val dynamicSourceKey: DynamicPaymentSourceKey?,
    val targetContext: HubTargetContext?,
    val replacesDynamicGuardId: String?
)

internal data class LnurlReviewRequest(
    val session: LnurlSession,
    val amountMsats: Long,
    val isManualEntry: Boolean,
    val paymentQuote: PaymentAmountQuote? = null
)

internal data class ApprovedLnurlReview(val request: LnurlReviewRequest)

internal sealed interface AdmissionResult {
    data object Presented : AdmissionResult

    data class Payment(val payment: PreparedPayment) : AdmissionResult

    data class LnurlReview(val review: LnurlReviewRequest) : AdmissionResult

    data class PendingClarification(
        val record: PendingRecord,
        val continuation: PendingRetryContinuation
    ) : AdmissionResult
}

internal sealed interface ConfirmationResult {
    data object Presented : ConfirmationResult

    data class Execute(val payment: ExecutablePayment) : ConfirmationResult

    data class ResolveLnurl(val approval: ApprovedLnurlReview) : ConfirmationResult
}

internal sealed interface ConfirmationDismissal {
    data object None : ConfirmationDismissal

    data object Active : ConfirmationDismissal

    data object ManualAmount : ConfirmationDismissal

    data object LnurlManualAmount : ConfirmationDismissal
}

internal data class PaymentExecutionAttempt(
    val pendingId: String,
    val invoice: Bolt11Invoice,
    val amountOverrideMsats: Long?,
    val targetContext: HubTargetContext?
)
