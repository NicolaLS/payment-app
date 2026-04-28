package xyz.lilsus.papp.domain.repository

import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.model.exchange.ExchangeRate

interface ExchangeRateRepository {
    suspend fun getExchangeRate(currencyCode: String): Result<ExchangeRate>
}
