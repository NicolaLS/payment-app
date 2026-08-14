package xyz.lilsus.flint

import xyz.lilsus.flint.integration.wallet.platform.createIOSWalletRuntime

fun createIOSAppHost(environment: FlintEnvironment, breezApiKey: String? = null): FlintAppHost {
    val bootstrapConfig = AppBootstrapConfig(environment.toAppEnvironment(), breezApiKey)
    return FlintAppHost(
        bootstrapConfig = bootstrapConfig,
        runtime = createIOSWalletRuntime(bootstrapConfig)
    )
}
