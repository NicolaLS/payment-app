package xyz.lilsus.raylsuite.feature.themesettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberThemePreferences(storageName: String): ThemePreferences {
    val settings = rememberPlatformThemeSettings(storageName)
    return remember(settings) {
        DefaultThemePreferences(settings)
    }
}
