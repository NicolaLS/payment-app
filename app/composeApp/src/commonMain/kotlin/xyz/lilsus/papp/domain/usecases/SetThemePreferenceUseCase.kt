package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.domain.repository.ThemePreferencesRepository

class SetThemePreferenceUseCase(private val repository: ThemePreferencesRepository) {
    suspend operator fun invoke(preference: ThemePreference) {
        repository.setThemePreference(preference)
    }
}
