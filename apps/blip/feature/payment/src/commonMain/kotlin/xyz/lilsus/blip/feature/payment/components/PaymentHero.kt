package xyz.lilsus.blip.feature.payment.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.payment.LoadingKind
import xyz.lilsus.blip.feature.payment.PaymentUiState
import xyz.lilsus.blip.feature.payment.generated.resources.Res
import xyz.lilsus.blip.feature.payment.generated.resources.payment_receipt_qr_content_description
import xyz.lilsus.raylsuite.core.camera.QrScannerMode
import xyz.lilsus.raylsuite.core.ui.hero.RaylHero
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroPhase
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroQrContent
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroScanMode

@Composable
fun PaymentHero(
    uiState: PaymentUiState,
    modifier: Modifier = Modifier,
    receiptPreimage: String? = null,
    scannerMode: QrScannerMode = QrScannerMode.Near,
    showScannerModeSelector: Boolean = false,
    onToggleScannerMode: (() -> Unit)? = null
) {
    val qrContent =
        receiptPreimage
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let {
                RaylHeroQrContent(
                    data = it,
                    contentDescription =
                        stringResource(Res.string.payment_receipt_qr_content_description)
                )
            }
    RaylHero(
        phase = uiState.toRaylHeroPhase(),
        modifier = modifier,
        qrContent = qrContent,
        scannerMode =
            when (scannerMode) {
                QrScannerMode.Near -> RaylHeroScanMode.Near
                QrScannerMode.Far -> RaylHeroScanMode.Far
            },
        showScannerModeSelector = showScannerModeSelector,
        onToggleScannerMode = onToggleScannerMode
    )
}

private fun PaymentUiState.toRaylHeroPhase(): RaylHeroPhase = when (this) {
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
