package xyz.lilsus.papp.data.exchange

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import xyz.lilsus.papp.data.network.createBaseHttpClient
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.model.exchange.ExchangeRate
import xyz.lilsus.papp.domain.repository.ExchangeRateRepository

private const val ASSET_ID = "bitcoin"
private const val CACHE_TTL_MS = 60 * 1000L // 1 minute

internal expect fun currentTimeMillis(): Long

class CoinGeckoExchangeRateRepository(
    private val client: HttpClient = createBaseHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ExchangeRateRepository {

    private val cacheMutex = Mutex()
    private val cache = mutableMapOf<String, CachedRate>()

    private data class CachedRate(val rate: ExchangeRate, val timestampMs: Long)

    override suspend fun getExchangeRate(currencyCode: String): Result<ExchangeRate> =
        withContext(dispatcher) {
            val normalized = currencyCode.uppercase()

            // Check cache first
            cacheMutex.withLock {
                cache[normalized]?.let { cached ->
                    val now = currentTimeMillis()
                    if (now - cached.timestampMs < CACHE_TTL_MS) {
                        return@withContext Result.Success(cached.rate)
                    }
                }
            }

            // Fetch from network
            val result = fetchFromNetwork(currencyCode)

            // Cache successful results
            if (result is Result.Success) {
                cacheMutex.withLock {
                    cache[normalized] = CachedRate(
                        rate = result.data,
                        timestampMs = currentTimeMillis()
                    )
                }
            }

            result
        }

    private suspend fun fetchFromNetwork(currencyCode: String): Result<ExchangeRate> {
        val normalized = currencyCode.lowercase()
        try {
            val response = client.get("https://api.coingecko.com/api/v3/simple/price") {
                parameter("ids", ASSET_ID)
                parameter("vs_currencies", normalized)
            }
            if (!response.status.isSuccess()) {
                return Result.Error(AppError.Unexpected("HTTP ${response.status.value}"))
            }

            val payload = response.body<JsonObject>()
            val coin = payload[ASSET_ID]?.jsonObject
            val pricePrimitive = coin?.get(normalized) as? JsonPrimitive
            val price = pricePrimitive?.doubleOrNull
            if (price == null) {
                return Result.Error(AppError.Unexpected("Unable to parse exchange rate"))
            }
            return Result.Success(
                ExchangeRate(
                    currencyCode = currencyCode.uppercase(),
                    pricePerBitcoin = price
                )
            )
        } catch (cause: Throwable) {
            val error = when (cause) {
                is io.ktor.utils.io.errors.IOException -> AppError.NetworkUnavailable
                else -> AppError.Unexpected(cause.message)
            }
            return Result.Error(error, cause)
        }
    }
}
