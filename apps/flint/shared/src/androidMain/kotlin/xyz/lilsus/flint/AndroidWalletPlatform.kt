package xyz.lilsus.flint

import android.content.Context
import xyz.lilsus.flint.integration.wallet.platform.createAndroidWalletRuntime

fun createAndroidAppHost(
    context: Context,
    environment: FlintEnvironment,
    breezApiKey: String? = null
): FlintAppHost {
    val bootstrapConfig = AppBootstrapConfig(environment.toAppEnvironment(), breezApiKey)
    return FlintAppHost(
        bootstrapConfig = bootstrapConfig,
        runtime = createAndroidWalletRuntime(context, bootstrapConfig)
    )
}
