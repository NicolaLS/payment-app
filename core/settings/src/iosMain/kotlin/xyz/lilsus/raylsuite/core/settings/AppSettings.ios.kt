package xyz.lilsus.raylsuite.core.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

@Composable
actual fun rememberAppSettings(storageName: String): Settings = remember(storageName) {
    NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
}
