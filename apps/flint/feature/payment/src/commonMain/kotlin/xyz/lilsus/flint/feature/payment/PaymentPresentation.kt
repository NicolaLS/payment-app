package xyz.lilsus.flint.feature.payment

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.flint.feature.payment.generated.resources.Res
import xyz.lilsus.flint.feature.payment.generated.resources.transaction_fee
import xyz.lilsus.flint.feature.payment.generated.resources.transaction_status_failure
import xyz.lilsus.flint.feature.payment.generated.resources.transaction_status_pending
import xyz.lilsus.flint.feature.payment.generated.resources.transaction_status_success
import xyz.lilsus.raylsuite.core.ui.format.AmountFormatter
import xyz.lilsus.raylsuite.feature.paymentui.PaymentLoadingKind
import xyz.lilsus.raylsuite.feature.paymentui.PaymentScreenState
import xyz.lilsus.raylsuite.feature.paymentui.PaymentSessionReference
import xyz.lilsus.raylsuite.feature.paymentui.PaymentSessionTransaction
import xyz.lilsus.raylsuite.feature.paymentui.PaymentStatusTone
import xyz.lilsus.raylsuite.feature.paymentui.PreviousPaymentSituation

@Composable
internal fun PaymentUiState.toPaymentScreenState(
    errorMessageFor: @Composable (PaymentUiError) -> String
): PaymentScreenState = when (this) {
    PaymentUiState.Active -> PaymentScreenState.Active

    PaymentUiState.Detected -> PaymentScreenState.Detected

    is PaymentUiState.Loading -> PaymentScreenState.Loading(kind.toPresentation())

    is PaymentUiState.EnterAmount -> PaymentScreenState.EnterAmount(entry)

    is PaymentUiState.Confirm -> PaymentScreenState.Confirm(amount)

    is PaymentUiState.PendingRetry -> PaymentScreenState.PendingRetry(id)

    is PaymentUiState.Success ->
        PaymentScreenState.Success(
            amountPaid = amountPaid,
            feePaid = feePaid,
            showEstimatedFeeHint = showEstimatedFeeHint,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimage
        )

    is PaymentUiState.Error -> PaymentScreenState.Error(errorMessageFor(error))
}

internal fun SessionTransactionItem.toPaymentSessionReference() = PaymentSessionReference(
    id = id,
    statusKey = status.name,
    previousPaymentSituation = status.toPreviousPaymentSituation()
)

@Composable
internal fun SessionTransactionItem.toPaymentSessionTransaction(
    formatter: AmountFormatter
): PaymentSessionTransaction {
    val presentation = status.presentation()
    return PaymentSessionTransaction(
        id = id,
        amount = amount,
        statusLabel = presentation.label,
        statusTone = presentation.tone,
        createdAtMs = createdAtMs,
        supportingText =
            when (status) {
                PendingStatus.Succeeded -> fee?.let {
                    stringResource(Res.string.transaction_fee, formatter.format(it))
                }

                PendingStatus.OutcomeUnknown,
                PendingStatus.Failed -> errorMessage

                PendingStatus.Sending,
                PendingStatus.Resolving -> null
            }
    )
}

private fun LoadingKind.toPresentation(): PaymentLoadingKind = when (this) {
    LoadingKind.Resolving -> PaymentLoadingKind.Resolving
    LoadingKind.Paying -> PaymentLoadingKind.Paying
}

private fun PendingStatus.toPreviousPaymentSituation(): PreviousPaymentSituation = when (this) {
    PendingStatus.Sending,
    PendingStatus.Resolving -> PreviousPaymentSituation.InProgress

    PendingStatus.OutcomeUnknown -> PreviousPaymentSituation.OutcomeUnknown

    PendingStatus.Succeeded,
    PendingStatus.Failed -> PreviousPaymentSituation.Completed
}

@Composable
private fun PendingStatus.presentation(): StatusPresentation = when (this) {
    PendingStatus.Sending,
    PendingStatus.Resolving ->
        StatusPresentation(
            stringResource(Res.string.transaction_status_pending),
            PaymentStatusTone.Pending
        )

    PendingStatus.Succeeded ->
        StatusPresentation(
            stringResource(Res.string.transaction_status_success),
            PaymentStatusTone.Success
        )

    PendingStatus.OutcomeUnknown,
    PendingStatus.Failed ->
        StatusPresentation(
            stringResource(Res.string.transaction_status_failure),
            PaymentStatusTone.Failure
        )
}

private data class StatusPresentation(val label: String, val tone: PaymentStatusTone)
