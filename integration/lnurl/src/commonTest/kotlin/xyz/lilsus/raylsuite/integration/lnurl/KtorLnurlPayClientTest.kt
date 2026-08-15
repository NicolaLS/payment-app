package xyz.lilsus.raylsuite.integration.lnurl

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.network.NetworkConnectivity
import xyz.lilsus.raylsuite.core.payment.LnurlResult

class KtorLnurlPayClientTest {
    @Test
    fun lightningAddressRequestPreservesUsernameAndTagCase() = runTest {
        var requestedHost: String? = null
        var requestedPath: String? = null
        val engine =
            MockEngine { request ->
                requestedHost = request.url.host
                requestedPath = request.url.encodedPath
                respond(
                    content =
                        """
                            {
                              "callback": "https://example.com/callback",
                              "maxSendable": 1000,
                              "minSendable": 1000,
                              "metadata": "[[\"text/plain\",\"Payment\"]]",
                              "tag": "payRequest"
                            }
                        """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        val client =
            KtorLnurlPayClient(
                networkConnectivity = AlwaysOnline,
                client = HttpClient(engine)
            )

        val result =
            client.fetchPayParams(
                LightningAddress(
                    username = "Alice",
                    domain = "Example.com",
                    tag = "Tips"
                )
            )

        assertIs<LnurlResult.Success<*>>(result)
        assertEquals("example.com", requestedHost)
        assertEquals("/.well-known/lnurlp/Alice+Tips", requestedPath)
    }

    private data object AlwaysOnline : NetworkConnectivity {
        override fun isNetworkAvailable(): Boolean = true
    }
}
