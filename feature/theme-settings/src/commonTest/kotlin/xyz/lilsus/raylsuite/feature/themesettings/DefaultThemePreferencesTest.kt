package xyz.lilsus.raylsuite.feature.themesettings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.ThemePreference

class DefaultThemePreferencesTest {
    @Test
    fun defaultsToSystemWhenUnset() = runTest {
        val preferences = DefaultThemePreferences(MapSettings())

        assertEquals(ThemePreference.System, preferences.current())
    }

    @Test
    fun persistsLightPreference() = runTest {
        val settings = MapSettings()
        val preferences = DefaultThemePreferences(settings)

        preferences.set(ThemePreference.Light)

        assertEquals(ThemePreference.Light, preferences.current())
        assertEquals(
            ThemePreference.Light,
            DefaultThemePreferences(settings).current()
        )
    }

    @Test
    fun selectingSystemRemovesOverride() = runTest {
        val settings = MapSettings()
        val preferences = DefaultThemePreferences(settings)

        preferences.set(ThemePreference.Dark)
        preferences.set(ThemePreference.System)

        assertEquals(
            ThemePreference.System,
            DefaultThemePreferences(settings).current()
        )
    }
}
