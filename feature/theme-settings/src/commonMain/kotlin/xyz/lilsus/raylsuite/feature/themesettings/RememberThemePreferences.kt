package xyz.lilsus.raylsuite.feature.themesettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings

@Composable
fun rememberThemePreferences(storageName: String): ThemePreferences {
    val settings = rememberAppSettings(storageName)
    return remember(settings) {
        DefaultThemePreferences(settings)
    }
}
