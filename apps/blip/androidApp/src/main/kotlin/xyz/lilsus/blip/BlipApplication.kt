package xyz.lilsus.blip

import android.app.Application
import xyz.lilsus.raylsuite.core.network.initializeNetworkConnectivity

class BlipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initializeNetworkConnectivity(this)
    }
}
