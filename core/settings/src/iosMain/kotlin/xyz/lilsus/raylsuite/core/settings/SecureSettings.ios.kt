package xyz.lilsus.raylsuite.core.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

@OptIn(ExperimentalSettingsImplementation::class)
@Composable
actual fun rememberSecureSettings(storageName: String): Settings {
    require(storageName.isNotBlank()) { "Secure storage name cannot be blank" }
    return remember(storageName) {
        KeychainSettings(service = storageName)
    }
}
