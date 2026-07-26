package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.repository.LanguageRepository

class ClearLanguageOverrideUseCase(private val repository: LanguageRepository) {
    suspend operator fun invoke() {
        repository.clearOverride()
    }
}
