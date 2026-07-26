package xyz.lilsus.blip.domain.repository

import xyz.lilsus.blip.domain.model.Result
import xyz.lilsus.blip.domain.model.exchange.ExchangeRate

interface ExchangeRateRepository {
    suspend fun getExchangeRate(currencyCode: String): Result<ExchangeRate>
}
