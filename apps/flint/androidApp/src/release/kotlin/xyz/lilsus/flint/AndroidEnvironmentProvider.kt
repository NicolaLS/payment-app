package xyz.lilsus.flint

internal fun androidFlintConfiguration(): AndroidFlintConfiguration = AndroidFlintConfiguration(
    environment = FlintEnvironment.PRODUCTION,
    breezApiKey = BuildConfig.BREEZ_API_KEY
)
