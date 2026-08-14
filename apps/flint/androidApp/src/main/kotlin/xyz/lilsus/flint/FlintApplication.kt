package xyz.lilsus.flint

import android.app.Application

class FlintApplication : Application() {
    val appHost: FlintAppHost by lazy {
        val configuration = androidFlintConfiguration()
        createAndroidAppHost(
            context = this,
            environment = configuration.environment,
            breezApiKey = configuration.breezApiKey
        )
    }
}

internal data class AndroidFlintConfiguration(
    val environment: FlintEnvironment,
    val breezApiKey: String? = null
)
