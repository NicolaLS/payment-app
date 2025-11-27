package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.LanguageRepository

class SetLanguagePreferenceUseCase(private val repository: LanguageRepository) {
    suspend operator fun invoke(tag: String) {
        repository.setLanguage(tag)
    }
}
