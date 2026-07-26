package xyz.lilsus.blip

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import xyz.lilsus.raylsuite.core.network.initializeNetworkConnectivity

class BlipApplication :
    Application(),
    CameraXConfig.Provider {

    override fun getCameraXConfig(): CameraXConfig = CameraXConfig.Builder
        .fromConfig(Camera2Config.defaultConfig())
        .setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
        .build()

    override fun onCreate() {
        super.onCreate()
        initializeNetworkConnectivity(this)
    }
}
