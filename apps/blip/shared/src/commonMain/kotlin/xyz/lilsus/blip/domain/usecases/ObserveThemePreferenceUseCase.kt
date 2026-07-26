package xyz.lilsus.blip.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.blip.domain.model.ThemePreference
import xyz.lilsus.blip.domain.repository.ThemePreferencesRepository

class ObserveThemePreferenceUseCase(private val repository: ThemePreferencesRepository) {
    operator fun invoke(): Flow<ThemePreference> = repository.preference
}
