package xyz.lilsus.raylsuite.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
interface QrScannerController {
    fun start(
        onQrCodeScanned: (String) -> Unit,
        onCameraPermissionMissing: () -> Unit = {},
        onScannerUnavailable: () -> Unit = {}
    ): Boolean

    fun stop()
}

@Composable
expect fun rememberQrScannerController(): QrScannerController
