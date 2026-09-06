package xyz.lilsus.raylsuite.core.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

/** Ordinary app preferences stay in the application's standard defaults domain. */
fun createAppSettings(): Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)

/** Connection data has a separate domain so removal cannot erase app preferences. */
fun createConnectionSettings(storageName: String): Settings {
    require(storageName.isNotBlank())
    return NSUserDefaultsSettings(NSUserDefaults(suiteName = storageName))
}
