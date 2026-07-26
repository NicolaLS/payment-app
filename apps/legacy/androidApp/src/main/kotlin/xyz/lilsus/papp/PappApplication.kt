package xyz.lilsus.papp

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import org.koin.core.context.startKoin
import xyz.lilsus.papp.di.nwcModule
import xyz.lilsus.papp.platform.AndroidAppContext
import xyz.lilsus.papp.platform.initializeNetworkConnectivity

class PappApplication :
    Application(),
    CameraXConfig.Provider {

    override fun getCameraXConfig(): CameraXConfig =
        CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            // Reduce startup latency for the cameras the application uses.
            .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
            .build()

    override fun onCreate() {
        super.onCreate()
        AndroidAppContext.initialize(this)
        initializeNetworkConnectivity(this)
        startKoin {
            modules(nwcModule)
        }
    }
}
