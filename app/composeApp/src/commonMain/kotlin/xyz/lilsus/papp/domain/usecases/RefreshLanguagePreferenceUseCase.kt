package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.LanguageRepository

class RefreshLanguagePreferenceUseCase(private val repository: LanguageRepository) {
    suspend operator fun invoke() {
        repository.refresh()
    }
}
