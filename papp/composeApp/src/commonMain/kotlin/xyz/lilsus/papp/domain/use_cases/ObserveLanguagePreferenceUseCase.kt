package xyz.lilsus.papp.domain.use_cases

import xyz.lilsus.papp.domain.model.LanguagePreference
import xyz.lilsus.papp.domain.repository.LanguageRepository

class ObserveLanguagePreferenceUseCase(private val repository: LanguageRepository) {
    operator fun invoke(): kotlinx.coroutines.flow.StateFlow<LanguagePreference> = repository.preference
}
