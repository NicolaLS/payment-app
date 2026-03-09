package xyz.lilsus.papp.presentation.main.scan

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

    fun start(onQrCodeScanned: (String) -> Unit)
    fun pause()
    fun resume()
    fun stop()
    fun bindPreview(surface: CameraPreviewSurface)
    fun unbindPreview()
    fun setMode(mode: QrScannerMode)
    fun setZoom(zoomFraction: Float)
}

@Stable
interface CameraPermissionState {
    val hasPermission: Boolean
    fun request()
}

expect class CameraPreviewSurface

@Composable
expect fun rememberQrScannerController(): QrScannerController

@Composable
expect fun rememberCameraPermissionState(): CameraPermissionState

@Composable
expect fun CameraPreviewHost(
    controller: QrScannerController,
    visible: Boolean,
    modifier: Modifier = Modifier,
    preferCompatibleMode: Boolean = false,
    onPreviewStreamingChanged: (Boolean) -> Unit = {}
)
