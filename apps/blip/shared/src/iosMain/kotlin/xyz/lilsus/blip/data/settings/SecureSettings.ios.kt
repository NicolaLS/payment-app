package xyz.lilsus.blip.data.settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import xyz.lilsus.blip.appStorageNamespace

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createSecureSettings(): Settings =
    KeychainSettings(service = "$appStorageNamespace.wallet")
