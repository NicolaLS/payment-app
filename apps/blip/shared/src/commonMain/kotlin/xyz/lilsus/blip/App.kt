package xyz.lilsus.blip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.themesettings.rememberThemePreferences

@Composable
fun App() {
    val themePreferences = rememberThemePreferences(storageName = "blip_preferences")
    val themePreference by themePreferences.preference.collectAsState(
        initial = ThemePreference.System
    )

    RaylSuiteTheme(themePreference = themePreference) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Hello World")
        }
    }
}
