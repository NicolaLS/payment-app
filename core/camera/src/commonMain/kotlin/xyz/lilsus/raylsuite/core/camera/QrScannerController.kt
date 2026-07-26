package xyz.lilsus.raylsuite.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

enum class QrScannerMode {
    Near,
    Far
}

@Stable
interface QrScannerController {
    val supportsManualModeSelection: Boolean

    fun start(
        onQrCodeScanned: (String) -> Unit,
        onCameraPermissionMissing: () -> Unit = {},
        onScannerUnavailable: () -> Unit = {}
    ): Boolean

    fun pause()

    fun resume()

    fun stop()

    fun bindPreview(surface: CameraPreviewSurface)

    fun unbindPreview()

    fun setMode(mode: QrScannerMode)

    fun setZoom(zoomFraction: Float)
}

expect class CameraPreviewSurface

@Composable
expect fun rememberQrScannerController(): QrScannerController

@Composable
expect fun CameraPreviewHost(
    controller: QrScannerController,
    visible: Boolean,
    modifier: Modifier = Modifier,
    preferCompatibleMode: Boolean = false,
    onPreviewStreamingChanged: (Boolean) -> Unit = {}
)
