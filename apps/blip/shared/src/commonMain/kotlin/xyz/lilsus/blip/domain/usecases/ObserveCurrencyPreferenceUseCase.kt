package xyz.lilsus.blip.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.lilsus.blip.domain.model.CurrencyCatalog
import xyz.lilsus.blip.domain.model.DisplayCurrency
import xyz.lilsus.blip.domain.repository.CurrencyPreferencesRepository

class ObserveCurrencyPreferenceUseCase(private val repository: CurrencyPreferencesRepository) {
    operator fun invoke(): Flow<DisplayCurrency> = repository.currencyCode.map { code ->
        CurrencyCatalog.infoFor(code).currency
    }
}
