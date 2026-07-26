package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.repository.LanguageRepository

class SetLanguagePreferenceUseCase(private val repository: LanguageRepository) {
    suspend operator fun invoke(tag: String) {
        repository.setLanguage(tag)
    }
}
