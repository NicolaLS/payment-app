package xyz.lilsus.raylsuite.feature.themesettings

import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings

@Composable
internal expect fun rememberPlatformThemeSettings(storageName: String): Settings
