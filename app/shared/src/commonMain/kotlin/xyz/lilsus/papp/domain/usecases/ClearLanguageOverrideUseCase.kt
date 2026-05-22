package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.LanguageRepository

class ClearLanguageOverrideUseCase(private val repository: LanguageRepository) {
    suspend operator fun invoke() {
        repository.clearOverride()
    }
}
