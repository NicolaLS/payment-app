package xyz.lilsus.blip.feature.payment

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import xyz.lilsus.blip.feature.payment.R
import xyz.lilsus.blip.integration.blink.BlinkWalletCurrency
import xyz.lilsus.raylsuite.core.ui.format.AmountFormatter
import xyz.lilsus.raylsuite.feature.paymentui.PaymentLoadingKind
import xyz.lilsus.raylsuite.feature.paymentui.PaymentScreenState
import xyz.lilsus.raylsuite.feature.paymentui.PaymentSessionReference
import xyz.lilsus.raylsuite.feature.paymentui.PaymentSessionTransaction
import xyz.lilsus.raylsuite.feature.paymentui.PaymentStatusTone
import xyz.lilsus.raylsuite.feature.paymentui.PreviousPaymentSituation

/** Android projection into the shared Compose payment renderer. */
@Composable
internal fun PaymentUiState.toPaymentScreenState(
    errorMessageFor: @Composable (PaymentUiError) -> String
): PaymentScreenState = when (this) {
    PaymentUiState.Active -> PaymentScreenState.Active

    PaymentUiState.Detected -> PaymentScreenState.Detected

    is PaymentUiState.Loading -> PaymentScreenState.Loading(kind.toPresentation())

    is PaymentUiState.EnterAmount -> PaymentScreenState.EnterAmount(entry, lnurlPayDisplay)

    is PaymentUiState.Confirm ->
        PaymentScreenState.Confirm(
            amount = amount,
            lnurlPayDisplay = lnurlPayDisplay,
            fundingSource =
                stringResource(
                    R.string.confirm_payment_funding_wallet,
                    fundingWallet.currency.androidTitle()
                )
        )

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
                PendingStatus.Success -> fee?.let {
                    stringResource(R.string.transaction_fee, formatter.format(it))
                }

                PendingStatus.StatusUnknown,
                PendingStatus.Failure -> errorMessage

                PendingStatus.Sending,
                PendingStatus.PendingInBlink,
                PendingStatus.AlreadyPaid -> null
            }
    )
}

private fun LoadingKind.toPresentation(): PaymentLoadingKind = when (this) {
    LoadingKind.Resolving -> PaymentLoadingKind.Resolving
    LoadingKind.Paying -> PaymentLoadingKind.Paying
}

@Composable
private fun BlinkWalletCurrency.androidTitle(): String = stringResource(
    when (this) {
        BlinkWalletCurrency.BTC -> R.string.funding_wallet_bitcoin
        BlinkWalletCurrency.USD -> R.string.funding_wallet_stablesats
    }
)

private fun PendingStatus.toPreviousPaymentSituation(): PreviousPaymentSituation = when (this) {
    PendingStatus.Sending,
    PendingStatus.PendingInBlink -> PreviousPaymentSituation.InProgress

    PendingStatus.StatusUnknown -> PreviousPaymentSituation.OutcomeUnknown

    PendingStatus.Success,
    PendingStatus.AlreadyPaid,
    PendingStatus.Failure -> PreviousPaymentSituation.Completed
}

@Composable
private fun PendingStatus.presentation(): StatusPresentation = when (this) {
    PendingStatus.Sending ->
        StatusPresentation(
            stringResource(R.string.transaction_status_sending),
            PaymentStatusTone.Pending
        )

    PendingStatus.PendingInBlink ->
        StatusPresentation(
            stringResource(R.string.transaction_status_pending_blink),
            PaymentStatusTone.Pending
        )

    PendingStatus.StatusUnknown ->
        StatusPresentation(
            stringResource(R.string.transaction_status_unknown),
            PaymentStatusTone.Failure
        )

    PendingStatus.Success ->
        StatusPresentation(
            stringResource(R.string.transaction_status_success),
            PaymentStatusTone.Success
        )

    PendingStatus.AlreadyPaid ->
        StatusPresentation(
            stringResource(R.string.transaction_status_already_paid),
            PaymentStatusTone.Success
        )

    PendingStatus.Failure ->
        StatusPresentation(
            stringResource(R.string.transaction_status_failure),
            PaymentStatusTone.Failure
        )
}

private data class StatusPresentation(val label: String, val tone: PaymentStatusTone)
