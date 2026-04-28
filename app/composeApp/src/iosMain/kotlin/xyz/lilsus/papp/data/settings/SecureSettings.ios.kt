package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings
import xyz.lilsus.papp.appStorageNamespace

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createSecureSettings(): Settings =
    KeychainSettings(service = "$appStorageNamespace.wallet")
