package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.domain.repository.ThemePreferencesRepository

private const val KEY_THEME_PREFERENCE = "theme.preference"

class ThemePreferencesRepositoryImpl(private val settings: Settings) : ThemePreferencesRepository {

    private val state = MutableStateFlow(loadPreference())

    override val preference: Flow<ThemePreference> = state.asStateFlow()

    override suspend fun getThemePreference(): ThemePreference = state.value

    override suspend fun setThemePreference(preference: ThemePreference) {
        if (preference == state.value) return
        persist(preference)
        state.value = preference
    }

    private fun loadPreference(): ThemePreference {
        val stored = settings.getStringOrNull(KEY_THEME_PREFERENCE)
            ?.trim()
            ?.lowercase()
            .orEmpty()
        return when (stored) {
            "light" -> ThemePreference.Light

            "dark" -> ThemePreference.Dark

            "system",
            "" -> ThemePreference.System

            else -> ThemePreference.System
        }
    }

    private fun persist(preference: ThemePreference) {
        when (preference) {
            ThemePreference.System -> settings.remove(KEY_THEME_PREFERENCE)
            ThemePreference.Light -> settings.putString(KEY_THEME_PREFERENCE, "light")
            ThemePreference.Dark -> settings.putString(KEY_THEME_PREFERENCE, "dark")
        }
    }
}
