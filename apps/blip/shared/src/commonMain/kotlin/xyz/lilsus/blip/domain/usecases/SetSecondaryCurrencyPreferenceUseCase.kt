package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.CurrencyCatalog
import xyz.lilsus.blip.domain.repository.CurrencyPreferencesRepository

class SetSecondaryCurrencyPreferenceUseCase(private val repository: CurrencyPreferencesRepository) {
    suspend operator fun invoke(code: String) {
        repository.setSecondaryCurrencyCode(CurrencyCatalog.infoFor(code).code)
    }
}
