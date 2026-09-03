package xyz.lilsus.raylsuite.core.camera

/** The shared start/stop contract implemented by CameraX on Android and AVFoundation on iOS. */
interface QrScannerController {
    fun start(
        onQrCodeScanned: (String) -> Unit,
        onCameraPermissionMissing: () -> Unit = {},
        onScannerUnavailable: () -> Unit = {}
    ): Boolean

    fun stop()
}
