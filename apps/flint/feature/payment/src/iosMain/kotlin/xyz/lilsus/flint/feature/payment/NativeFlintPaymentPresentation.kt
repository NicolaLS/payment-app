package xyz.lilsus.flint.feature.payment

import org.jetbrains.compose.resources.getString
import xyz.lilsus.flint.feature.payment.generated.resources.Res
import xyz.lilsus.flint.feature.payment.generated.resources.transaction_fee
import xyz.lilsus.flint.feature.payment.generated.resources.transaction_status_failure
import xyz.lilsus.flint.feature.payment.generated.resources.transaction_status_pending
import xyz.lilsus.flint.feature.payment.generated.resources.transaction_status_success
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.format.AmountFormatter
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.feature.paymentui.NativePaymentRecentItem
import xyz.lilsus.raylsuite.feature.paymentui.PaymentLoadingKind
import xyz.lilsus.raylsuite.feature.paymentui.PaymentScreenState
import xyz.lilsus.raylsuite.feature.paymentui.PreviousPaymentSituation

/** Flint-owned projection into the provider-neutral native Scan presentation state. */
suspend fun PaymentUiState.toNativePaymentScreenState(): PaymentScreenState = when (this) {
    PaymentUiState.Active -> PaymentScreenState.Active

    PaymentUiState.Detected -> PaymentScreenState.Detected

    is PaymentUiState.Loading ->
        PaymentScreenState.Loading(
            if (kind == LoadingKind.Resolving) {
                PaymentLoadingKind.Resolving
            } else {
                PaymentLoadingKind.Paying
            }
        )

    is PaymentUiState.EnterAmount -> PaymentScreenState.EnterAmount(entry, lnurlPayDisplay)

    is PaymentUiState.Confirm -> PaymentScreenState.Confirm(amount, lnurlPayDisplay)

    is PaymentUiState.PendingRetry -> PaymentScreenState.PendingRetry(id)

    is PaymentUiState.Success ->
        PaymentScreenState.Success(
            amountPaid = amountPaid,
            feePaid = feePaid,
            showEstimatedFeeHint = showEstimatedFeeHint,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimage
        )

    is PaymentUiState.Error -> PaymentScreenState.Error(getFlintPaymentErrorMessageFor(error))
}

fun SessionTransactionItem.previousPaymentSituation(): PreviousPaymentSituation = when (status) {
    PendingStatus.Sending,
    PendingStatus.Resolving -> PreviousPaymentSituation.InProgress

    PendingStatus.OutcomeUnknown -> PreviousPaymentSituation.OutcomeUnknown

    PendingStatus.Succeeded,
    PendingStatus.Failed -> PreviousPaymentSituation.Completed
}

suspend fun SessionTransactionItem.toNativeRecentItem(
    formatter: AmountFormatter = currentAmountFormatter()
): NativePaymentRecentItem = NativePaymentRecentItem(
    id = id,
    amount = formatter.format(amount),
    statusLabel =
        getString(
            when (status) {
                PendingStatus.Sending,
                PendingStatus.Resolving -> Res.string.transaction_status_pending

                PendingStatus.Succeeded -> Res.string.transaction_status_success

                PendingStatus.OutcomeUnknown,
                PendingStatus.Failed -> Res.string.transaction_status_failure
            }
        ),
    statusTone = status.nativeStatusTone(),
    createdAtMs = createdAtMs,
    supportingText =
        when (status) {
            PendingStatus.Succeeded -> fee?.let {
                getString(Res.string.transaction_fee, formatter.format(it))
            }

            PendingStatus.OutcomeUnknown,
            PendingStatus.Failed ->
                getFlintPaymentErrorMessageFor(
                    error ?: PaymentUiError.Unexpected(errorMessage)
                )

            PendingStatus.Sending,
            PendingStatus.Resolving -> null
        },
    detailState = toNativeDetailState(),
    canRetry = status == PendingStatus.OutcomeUnknown,
    pendingMessage = null
)

private suspend fun SessionTransactionItem.toNativeDetailState(): PaymentScreenState =
    when (status) {
        PendingStatus.Sending,
        PendingStatus.Resolving -> PaymentUiState.Loading()

        PendingStatus.Succeeded -> {
            val paidAmount = resultAmount ?: amount
            PaymentUiState.Success(
                amountPaid = paidAmount,
                feePaid = fee ?: paidAmount.zero(),
                showEstimatedFeeHint = showEstimatedFeeHint && !wasAlreadyPaid,
                wasAlreadyPaid = wasAlreadyPaid,
                preimage = preimage
            )
        }

        PendingStatus.OutcomeUnknown,
        PendingStatus.Failed ->
            PaymentUiState.Error(error ?: PaymentUiError.Unexpected(errorMessage))
    }.toNativePaymentScreenState()

private fun PendingStatus.nativeStatusTone(): String = when (this) {
    PendingStatus.Sending,
    PendingStatus.Resolving -> "pending"

    PendingStatus.Succeeded -> "success"

    PendingStatus.OutcomeUnknown,
    PendingStatus.Failed -> "failure"
}

private fun DisplayAmount.zero(): DisplayAmount = DisplayAmount(0, currency)
