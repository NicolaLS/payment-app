package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.CurrencyCatalog
import xyz.lilsus.blip.domain.repository.CurrencyPreferencesRepository

class SetCurrencyPreferenceUseCase(private val repository: CurrencyPreferencesRepository) {
    suspend operator fun invoke(code: String) {
        repository.setCurrencyCode(CurrencyCatalog.infoFor(code).code)
    }
}
