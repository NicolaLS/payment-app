package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.Result
import xyz.lilsus.blip.domain.model.exchange.ExchangeRate
import xyz.lilsus.blip.domain.repository.ExchangeRateRepository

class GetExchangeRateUseCase(private val repository: ExchangeRateRepository) {
    suspend operator fun invoke(currencyCode: String): Result<ExchangeRate> =
        repository.getExchangeRate(currencyCode)
}
