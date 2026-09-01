package xyz.lilsus.raylsuite.integration.exchangerate

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import xyz.lilsus.raylsuite.core.network.createHttpClient
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider

class CoinGeckoBitcoinPriceProvider(
    private val client: HttpClient = createHttpClient(),
    private val clock: () -> Long = ::platformCurrentTimeMillis
) : BitcoinPriceProvider {
    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CachedPrice>()

    override suspend fun pricePerBitcoin(fiatCurrencyCode: String): Double? {
        val currencyCode =
            fiatCurrencyCode
                .trim()
                .uppercase()
                .takeIf(String::isNotEmpty)
                ?: return null
        return cacheMutex.withLock {
            val cached = cache[currencyCode]
            if (cached != null && clock() - cached.storedAtMs < CACHE_TTL_MS) {
                return@withLock cached.price
            }
            cache.remove(currencyCode)
            val price = fetchPrice(currencyCode) ?: return@withLock null
            cache[currencyCode] = CachedPrice(price = price, storedAtMs = clock())
            price
        }
    }

    private suspend fun fetchPrice(currencyCode: String): Double? = try {
        val response =
            client.get(PRICE_ENDPOINT) {
                parameter("ids", BITCOIN_ASSET_ID)
                parameter("vs_currencies", currencyCode.lowercase())
            }
        if (!response.status.isSuccess()) return null

        val payload = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        payload
            .bitcoinPrice(currencyCode)
            ?.takeIf { it.isFinite() && it > 0.0 }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private data class CachedPrice(val price: Double, val storedAtMs: Long)

    private companion object {
        const val BITCOIN_ASSET_ID = "bitcoin"
        const val PRICE_ENDPOINT = "https://api.coingecko.com/api/v3/simple/price"
        const val CACHE_TTL_MS = 60_000L
    }
}

private fun JsonObject.bitcoinPrice(currencyCode: String): Double? {
    val bitcoin = get(BITCOIN_KEY)?.jsonObject
    return (bitcoin?.get(currencyCode.lowercase()) as? JsonPrimitive)?.doubleOrNull
}

private const val BITCOIN_KEY = "bitcoin"

internal expect fun platformCurrentTimeMillis(): Long
