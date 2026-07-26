package xyz.lilsus.raylsuite.feature.themesettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberPlatformThemeSettings(storageName: String): Settings =
    remember(storageName) {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }
