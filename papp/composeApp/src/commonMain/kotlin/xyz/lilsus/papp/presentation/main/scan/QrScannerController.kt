package xyz.lilsus.papp.presentation.main.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
interface QrScannerController {
    fun start(onQrCodeScanned: (String) -> Unit)
    fun pause()
    fun resume()
    fun stop()
}

@Stable
interface CameraPermissionState {
    val hasPermission: Boolean
    fun request()
}

@Composable
expect fun rememberQrScannerController(): QrScannerController

@Composable
expect fun rememberCameraPermissionState(): CameraPermissionState
