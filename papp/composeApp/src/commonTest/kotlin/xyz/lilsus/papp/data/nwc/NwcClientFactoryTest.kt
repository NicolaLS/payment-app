package xyz.lilsus.papp.data.nwc

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType

class NwcClientFactoryTest {

    @Test
    fun invalidConnectionUriThrowsTypedWalletUriError() = runTest {
        val httpClient = HttpClient(MockEngine { respond("") })
        try {
            val factory = RealNwcClientFactory(httpClient = httpClient, scope = this)

            val exception = assertFailsWith<AppErrorException> {
                factory.create(
                    WalletConnection(
                        walletPublicKey = "nwc-wallet",
                        alias = "NWC",
                        type = WalletType.NWC,
                        uri = "not-a-nwc-uri"
                    )
                )
            }

            assertEquals(AppError.InvalidWalletUri("Invalid NWC URI"), exception.error)
        } finally {
            httpClient.close()
        }
    }
}
