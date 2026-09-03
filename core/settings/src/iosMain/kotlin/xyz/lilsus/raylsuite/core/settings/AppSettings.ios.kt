package xyz.lilsus.raylsuite.core.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

/** App-scoped storage for the native iOS shell, which owns the app scope. */
fun createAppSettings(@Suppress("UNUSED_PARAMETER") storageName: String): Settings =
    NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
