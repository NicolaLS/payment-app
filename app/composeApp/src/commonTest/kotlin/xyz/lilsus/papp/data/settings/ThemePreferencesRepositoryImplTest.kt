package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.ThemePreference

class ThemePreferencesRepositoryImplTest {

    @Test
    fun defaultsToSystem_whenUnset() = runTest {
        val settings = MapSettings()
        val repository = ThemePreferencesRepositoryImpl(settings)

        assertEquals(ThemePreference.System, repository.getThemePreference())
    }

    @Test
    fun persistsLightPreference() = runTest {
        val settings = MapSettings()
        val repository = ThemePreferencesRepositoryImpl(settings)

        repository.setThemePreference(ThemePreference.Light)

        assertEquals(ThemePreference.Light, repository.getThemePreference())
        assertEquals(ThemePreference.Light, ThemePreferencesRepositoryImpl(settings).getThemePreference())
    }

    @Test
    fun clearingToSystemRemovesOverride() = runTest {
        val settings = MapSettings()
        val repository = ThemePreferencesRepositoryImpl(settings)

        repository.setThemePreference(ThemePreference.Dark)
        repository.setThemePreference(ThemePreference.System)

        assertEquals(ThemePreference.System, ThemePreferencesRepositoryImpl(settings).getThemePreference())
    }
}
