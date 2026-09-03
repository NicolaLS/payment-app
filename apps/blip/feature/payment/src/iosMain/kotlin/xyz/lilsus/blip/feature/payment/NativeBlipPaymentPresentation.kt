package xyz.lilsus.blip.feature.payment

import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.format.AmountFormatter
import xyz.lilsus.raylsuite.core.ui.format.currentAmountFormatter
import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString
import xyz.lilsus.raylsuite.feature.paymentui.NativePaymentRecentItem
import xyz.lilsus.raylsuite.feature.paymentui.PaymentLoadingKind
import xyz.lilsus.raylsuite.feature.paymentui.PaymentScreenState
import xyz.lilsus.raylsuite.feature.paymentui.PreviousPaymentSituation

/** Blip-owned projection into the provider-neutral native Scan presentation state. */
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

    is PaymentUiState.Error -> PaymentScreenState.Error(getBlipPaymentErrorMessageFor(error))
}

fun SessionTransactionItem.previousPaymentSituation(): PreviousPaymentSituation = when (status) {
    PendingStatus.Sending,
    PendingStatus.PendingInBlink -> PreviousPaymentSituation.InProgress

    PendingStatus.StatusUnknown -> PreviousPaymentSituation.OutcomeUnknown

    PendingStatus.Success,
    PendingStatus.AlreadyPaid,
    PendingStatus.Failure -> PreviousPaymentSituation.Completed
}

suspend fun SessionTransactionItem.toNativeRecentItem(
    formatter: AmountFormatter = currentAmountFormatter()
): NativePaymentRecentItem = NativePaymentRecentItem(
    id = id,
    amount = formatter.format(amount),
    statusLabel = status.nativeStatusLabel(),
    statusTone = status.nativeStatusTone(),
    createdAtMs = createdAtMs,
    supportingText =
        when (status) {
            PendingStatus.Success -> fee?.let {
                nativeString(
                    NativeStringResource(table = "BlipPayment", key = "transaction_fee"),
                    formatter.format(it)
                )
            }

            PendingStatus.StatusUnknown,
            PendingStatus.Failure ->
                getBlipPaymentErrorMessageFor(
                    error ?: PaymentUiError.Unexpected(errorMessage)
                )

            PendingStatus.Sending,
            PendingStatus.PendingInBlink,
            PendingStatus.AlreadyPaid -> null
        },
    detailState = toNativeDetailState(),
    canRetry = status == PendingStatus.StatusUnknown || status == PendingStatus.Failure,
    pendingMessage =
        nativeString(NativeStringResource(table = "BlipPayment", key = "tap_dismiss_pending_blink"))
            .takeIf { status == PendingStatus.PendingInBlink }
)

private suspend fun SessionTransactionItem.toNativeDetailState(): PaymentScreenState =
    when (status) {
        PendingStatus.Sending,
        PendingStatus.PendingInBlink -> PaymentUiState.Loading()

        PendingStatus.Success,
        PendingStatus.AlreadyPaid -> {
            val paidAmount = resultAmount ?: amount
            PaymentUiState.Success(
                amountPaid = paidAmount,
                feePaid = fee ?: paidAmount.zero(),
                showEstimatedFeeHint = showEstimatedFeeHint && !wasAlreadyPaid,
                wasAlreadyPaid = status == PendingStatus.AlreadyPaid || wasAlreadyPaid,
                preimage = preimage
            )
        }

        PendingStatus.Failure,
        PendingStatus.StatusUnknown ->
            PaymentUiState.Error(error ?: PaymentUiError.Unexpected(errorMessage))
    }.toNativePaymentScreenState()

private suspend fun PendingStatus.nativeStatusLabel(): String = nativeString(
    when (this) {
        PendingStatus.Sending -> NativeStringResource(
            table = "BlipPayment",
            key = "transaction_status_sending"
        )

        PendingStatus.PendingInBlink -> NativeStringResource(
            table = "BlipPayment",
            key = "transaction_status_pending_blink"
        )

        PendingStatus.StatusUnknown -> NativeStringResource(
            table = "BlipPayment",
            key = "transaction_status_unknown"
        )

        PendingStatus.Success -> NativeStringResource(
            table = "BlipPayment",
            key = "transaction_status_success"
        )

        PendingStatus.AlreadyPaid -> NativeStringResource(
            table = "BlipPayment",
            key = "transaction_status_already_paid"
        )

        PendingStatus.Failure -> NativeStringResource(
            table = "BlipPayment",
            key = "transaction_status_failure"
        )
    }
)

private fun PendingStatus.nativeStatusTone(): String = when (this) {
    PendingStatus.Sending,
    PendingStatus.PendingInBlink -> "pending"

    PendingStatus.Success,
    PendingStatus.AlreadyPaid -> "success"

    PendingStatus.StatusUnknown,
    PendingStatus.Failure -> "failure"
}

private fun DisplayAmount.zero(): DisplayAmount = DisplayAmount(0, currency)
