package xyz.lilsus.papp

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import java.lang.ref.WeakReference
import org.koin.core.context.startKoin
import xyz.lilsus.papp.di.nwcModule
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
        instance = this
        initializeNetworkConnectivity(this)
        startKoin {
            modules(nwcModule)
        }
    }

    fun registerActivity(activity: MainActivity) {
        currentActivity = WeakReference(activity)
    }

    fun unregisterActivity(activity: MainActivity) {
        currentActivity?.get()?.let {
            if (it === activity) {
                currentActivity = null
            }
        }
    }

    fun recreateTopActivity() {
        currentActivity?.get()?.let { activity ->
            activity.runOnUiThread { activity.recreate() }
        }
    }

    companion object {
        lateinit var instance: PappApplication
            private set

        private var currentActivity: WeakReference<MainActivity>? = null
    }
}
