package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.repository.CurrencyPreferencesRepository

class SetSecondaryCurrencyPreferenceUseCase(private val repository: CurrencyPreferencesRepository) {
    suspend operator fun invoke(code: String) {
        repository.setSecondaryCurrencyCode(CurrencyCatalog.infoFor(code).code)
    }
}
