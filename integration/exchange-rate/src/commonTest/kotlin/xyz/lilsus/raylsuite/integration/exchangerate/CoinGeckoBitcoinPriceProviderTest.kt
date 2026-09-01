package xyz.lilsus.raylsuite.integration.exchangerate

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest

class CoinGeckoBitcoinPriceProviderTest {
    @Test
    fun cachesSuccessfulPriceByNormalizedCurrencyCode() = runTest {
        var requestCount = 0
        val client =
            HttpClient(
                MockEngine {
                    requestCount += 1
                    delay(10)
                    respond(
                        content = """{"bitcoin":{"usd":60000.0}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            )
        val provider = CoinGeckoBitcoinPriceProvider(client = client, clock = { 100L })

        assertEquals(60_000.0, provider.pricePerBitcoin("usd"))
        assertEquals(60_000.0, provider.pricePerBitcoin("USD"))
        assertEquals(1, requestCount)

        client.close()
    }

    @Test
    fun coalescesConcurrentCacheMisses() = runTest {
        var requestCount = 0
        val client =
            HttpClient(
                MockEngine {
                    requestCount += 1
                    respond(
                        content = """{"bitcoin":{"usd":60000.0}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            )
        val provider = CoinGeckoBitcoinPriceProvider(client = client, clock = { 100L })

        List(5) {
            async { provider.pricePerBitcoin("USD") }
        }.awaitAll()

        assertEquals(1, requestCount)
        client.close()
    }
}
