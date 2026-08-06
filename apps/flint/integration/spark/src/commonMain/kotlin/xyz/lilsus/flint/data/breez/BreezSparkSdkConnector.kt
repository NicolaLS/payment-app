package xyz.lilsus.flint.data.breez

import breez_sdk_spark.ConnectRequest
import breez_sdk_spark.Seed
import breez_sdk_spark.connect
import xyz.lilsus.flint.AppBootstrapConfig
import xyz.lilsus.flint.application.wallet.SparkSdkConnector
import xyz.lilsus.flint.application.wallet.SparkSdkSession
import xyz.lilsus.flint.application.wallet.WalletCredential

object BreezSparkSdkConnector : SparkSdkConnector {
    override suspend fun connect(
        credential: WalletCredential,
        storageDirectory: String,
        bootstrapConfig: AppBootstrapConfig
    ): SparkSdkSession {
        val sdk = connect(
            ConnectRequest(
                config = bootstrapConfig.sparkConfig(),
                seed = Seed.Mnemonic(credential.value, null),
                storageDir = storageDirectory
            )
        )
        return object : SparkSdkSession {
            override val paymentClient = BreezSparkPaymentClient(sdk)
            override suspend fun disconnect() = sdk.disconnect()
        }
    }
}
