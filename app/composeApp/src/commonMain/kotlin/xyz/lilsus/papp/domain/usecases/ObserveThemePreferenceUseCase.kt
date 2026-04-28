package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.domain.repository.ThemePreferencesRepository

class ObserveThemePreferenceUseCase(private val repository: ThemePreferencesRepository) {
    operator fun invoke(): Flow<ThemePreference> = repository.preference
}
