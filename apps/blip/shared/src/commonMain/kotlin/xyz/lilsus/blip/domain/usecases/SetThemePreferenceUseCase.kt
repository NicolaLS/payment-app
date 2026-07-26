package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.ThemePreference
import xyz.lilsus.blip.domain.repository.ThemePreferencesRepository

class SetThemePreferenceUseCase(private val repository: ThemePreferencesRepository) {
    suspend operator fun invoke(preference: ThemePreference) {
        repository.setThemePreference(preference)
    }
}
