package xyz.lilsus.raylsuite.feature.themesettings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.raylsuite.core.model.ThemePreference

interface ThemePreferences {
    val preference: Flow<ThemePreference>

    suspend fun current(): ThemePreference

    suspend fun set(preference: ThemePreference)
}

class DefaultThemePreferences(private val settings: Settings) : ThemePreferences {
    private val state = MutableStateFlow(loadPreference())

    override val preference: Flow<ThemePreference> = state.asStateFlow()

    override suspend fun current(): ThemePreference = state.value

    override suspend fun set(preference: ThemePreference) {
        if (preference == state.value) return

        when (preference) {
            ThemePreference.System -> settings.remove(KEY_THEME_PREFERENCE)
            ThemePreference.Light -> settings.putString(KEY_THEME_PREFERENCE, "light")
            ThemePreference.Dark -> settings.putString(KEY_THEME_PREFERENCE, "dark")
        }
        state.value = preference
    }

    private fun loadPreference(): ThemePreference = when (
        settings
            .getStringOrNull(KEY_THEME_PREFERENCE)
            ?.trim()
            ?.lowercase()
            .orEmpty()
    ) {
        "light" -> ThemePreference.Light
        "dark" -> ThemePreference.Dark
        else -> ThemePreference.System
    }

    private companion object {
        const val KEY_THEME_PREFERENCE = "theme.preference"
    }
}
