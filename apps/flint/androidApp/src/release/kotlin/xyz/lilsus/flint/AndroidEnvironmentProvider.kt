package xyz.lilsus.flint

internal fun androidBootstrapConfig(): AppBootstrapConfig = AppBootstrapConfig(
    environment = AppEnvironment.PRODUCTION,
    breezApiKey = BuildConfig.BREEZ_API_KEY
)
