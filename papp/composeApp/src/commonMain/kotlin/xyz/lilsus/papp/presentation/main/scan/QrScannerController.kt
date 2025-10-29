package xyz.lilsus.papp.presentation.main.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

@Stable
interface QrScannerController {
    fun start(onQrCodeScanned: (String) -> Unit)
    fun pause()
    fun resume()
    fun stop()
    fun bindPreview(surface: CameraPreviewSurface)
    fun unbindPreview()
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
)
