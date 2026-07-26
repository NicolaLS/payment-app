package xyz.lilsus.raylsuite.feature.themesettings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.ThemePreference

class ThemeSettingsViewModelTest {
    @Test
    fun selectionUpdatesStateAndPersistence() = runTest {
        val preferences = DefaultThemePreferences(MapSettings())
        val viewModel =
            ThemeSettingsViewModel(
                preferences = preferences,
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        viewModel.selectTheme(ThemePreference.Dark)
        testScheduler.advanceUntilIdle()

        assertEquals(ThemePreference.Dark, viewModel.uiState.value.selected)
        assertEquals(ThemePreference.Dark, preferences.current())

        viewModel.clear()
    }
}
