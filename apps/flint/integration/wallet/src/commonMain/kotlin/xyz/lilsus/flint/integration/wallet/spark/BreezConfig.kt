package xyz.lilsus.flint.integration.wallet.spark

import breez_sdk_spark.Config
import breez_sdk_spark.Network
import breez_sdk_spark.defaultConfig
import xyz.lilsus.flint.application.AppBootstrapConfig
import xyz.lilsus.flint.application.AppEnvironment

fun AppBootstrapConfig.sparkConfig(): Config =
    defaultConfig(environment.sparkNetwork()).also { it.apiKey = sdkApiKey() }

fun AppEnvironment.sparkNetwork(): Network = when (this) {
    AppEnvironment.DEBUG -> Network.REGTEST
    AppEnvironment.PRODUCTION -> Network.MAINNET
}
