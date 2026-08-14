package xyz.lilsus.raylsuite.feature.paymentui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.camera.QrScannerMode
import xyz.lilsus.raylsuite.core.ui.hero.RaylHero
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroPhase
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroQrContent
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroScanMode
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.payment_receipt_qr_content_description

@Composable
fun PaymentHero(
    phase: RaylHeroPhase,
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
                    contentDescription = stringResource(
                        Res.string.payment_receipt_qr_content_description
                    )
                )
            }
    RaylHero(
        phase = phase,
        modifier = modifier,
        qrContent = qrContent,
        scannerMode = when (scannerMode) {
            QrScannerMode.Near -> RaylHeroScanMode.Near
            QrScannerMode.Far -> RaylHeroScanMode.Far
        },
        showScannerModeSelector = showScannerModeSelector,
        onToggleScannerMode = onToggleScannerMode
    )
}
