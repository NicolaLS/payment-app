package xyz.lilsus.lasr.feature.payment

import androidx.compose.runtime.Composable
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroPhase
import xyz.lilsus.raylsuite.feature.paymentui.components.PaymentResultPresentation

internal fun PaymentUiState.toHeroPhase(): RaylHeroPhase = when (this) {
    PaymentUiState.Active -> RaylHeroPhase.Ready

    is PaymentUiState.Detected,
    is PaymentUiState.Confirm,
    is PaymentUiState.EnterAmount,
    is PaymentUiState.PendingRetry -> RaylHeroPhase.Acknowledged

    is PaymentUiState.Loading ->
        if (kind == LoadingKind.Resolving) {
            RaylHeroPhase.Acknowledged
        } else {
            RaylHeroPhase.Processing
        }

    is PaymentUiState.Success -> RaylHeroPhase.Succeeded

    is PaymentUiState.Error -> RaylHeroPhase.Failed
}

@Composable
internal fun PaymentUiState.toResultPresentation(
    errorMessageFor: @Composable (PaymentUiError) -> String
): PaymentResultPresentation = when (this) {
    is PaymentUiState.Success ->
        PaymentResultPresentation.Success(
            amountPaid = amountPaid,
            feePaid = feePaid,
            showEstimatedFeeHint = showEstimatedFeeHint,
            wasAlreadyPaid = wasAlreadyPaid,
            hasReceipt = !preimage.isNullOrBlank()
        )

    is PaymentUiState.Error -> PaymentResultPresentation.Error(errorMessageFor(error))

    else -> error("Only terminal payment states have result presentation")
}
