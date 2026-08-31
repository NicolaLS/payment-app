package xyz.lilsus.blip

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import xyz.lilsus.raylsuite.core.camera.CameraPreviewHost
import xyz.lilsus.raylsuite.core.camera.rememberQrScannerController

/** Release-like, wallet-free entry point used only by the external macrobenchmark. */
class CameraBenchmarkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scannerController = rememberQrScannerController()

            DisposableEffect(scannerController) {
                scannerController.start(onQrCodeScanned = {})
                onDispose(scannerController::stop)
            }

            CameraPreviewHost(
                controller = scannerController,
                visible = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
