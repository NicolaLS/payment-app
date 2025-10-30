package xyz.lilsus.papp.data.exchange

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.lilsus.papp.data.network.createBaseHttpClient
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.model.exchange.ExchangeRate
import xyz.lilsus.papp.domain.repository.ExchangeRateRepository

private const val ASSET_ID = "bitcoin"

class CoinGeckoExchangeRateRepository(
    private val client: HttpClient = createBaseHttpClient(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ExchangeRateRepository {

    override suspend fun getExchangeRate(currencyCode: String): Result<ExchangeRate> =
        withContext(dispatcher) {
            val normalized = currencyCode.lowercase()
            try {
                val response = client.get("https://api.coingecko.com/api/v3/simple/price") {
                    parameter("ids", ASSET_ID)
                    parameter("vs_currencies", normalized)
                }
                if (!response.status.isSuccess()) {
                    return@withContext Result.Error(AppError.Unexpected("HTTP ${response.status.value}"))
                }

                val payload = response.body<JsonObject>()
                val coin = payload[ASSET_ID]?.jsonObject
                val pricePrimitive = coin?.get(normalized) as? JsonPrimitive
                val price = pricePrimitive?.doubleOrNull
                if (price == null) {
                    return@withContext Result.Error(AppError.Unexpected("Unable to parse exchange rate"))
                }
                Result.Success(
                    ExchangeRate(
                        currencyCode = currencyCode.uppercase(),
                        pricePerBitcoin = price,
                    )
                )
            } catch (cause: Throwable) {
                val error = when (cause) {
                    is io.ktor.utils.io.errors.IOException -> AppError.NetworkUnavailable
                    else -> AppError.Unexpected(cause.message)
                }
                Result.Error(error, cause)
            }
        }
}
