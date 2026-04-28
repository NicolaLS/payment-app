package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.model.exchange.ExchangeRate
import xyz.lilsus.papp.domain.repository.ExchangeRateRepository

class GetExchangeRateUseCase(private val repository: ExchangeRateRepository) {
    suspend operator fun invoke(currencyCode: String): Result<ExchangeRate> =
        repository.getExchangeRate(currencyCode)
}
