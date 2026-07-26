package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.LanguagePreference
import xyz.lilsus.blip.domain.repository.LanguageRepository

class ObserveLanguagePreferenceUseCase(private val repository: LanguageRepository) {
    operator fun invoke(): kotlinx.coroutines.flow.StateFlow<LanguagePreference> =
        repository.preference
}
