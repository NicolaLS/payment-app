package xyz.lilsus.papp.domain.use_cases

import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.repository.CurrencyPreferencesRepository

class SetCurrencyPreferenceUseCase(
    private val repository: CurrencyPreferencesRepository,
) {
    suspend operator fun invoke(code: String) {
        repository.setCurrencyCode(CurrencyCatalog.infoFor(code).code)
    }
}
