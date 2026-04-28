package xyz.lilsus.papp.data.lnurl

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
import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.Result

class LnurlRepositoryImplTest {

    @Test
    fun lightningAddressRequestPreservesUsernameAndTagCase() = runTest {
        var requestedHost: String? = null
        var requestedPath: String? = null
        val engine = MockEngine { request ->
            requestedHost = request.url.host
            requestedPath = request.url.encodedPath
            respond(
                content = """
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
        val repository = LnurlRepositoryImpl(client = HttpClient(engine))

        val result = repository.fetchPayParams(
            LightningAddress(
                username = "Alice",
                domain = "Example.com",
                tag = "Tips"
            )
        )

        assertIs<Result.Success<*>>(result)
        assertEquals("example.com", requestedHost)
        assertEquals("/.well-known/lnurlp/Alice+Tips", requestedPath)
    }
}
