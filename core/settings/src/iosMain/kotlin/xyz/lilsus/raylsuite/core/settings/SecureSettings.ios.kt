package xyz.lilsus.raylsuite.core.settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

/** App-scoped storage for the native iOS shell, which owns the app scope. */
@OptIn(ExperimentalSettingsImplementation::class)
fun createSecureSettings(storageName: String): Settings {
    require(storageName.isNotBlank()) { "Secure storage name cannot be blank" }
    return KeychainSettings(service = storageName)
}
