package xyz.lilsus.lasr.feature.walletconnection

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun NwcConnectionQrScannerPreview(
    onQrCodeScanned: (String) -> Unit,
    onCameraPermissionMissing: () -> Unit,
    modifier: Modifier = Modifier
)
