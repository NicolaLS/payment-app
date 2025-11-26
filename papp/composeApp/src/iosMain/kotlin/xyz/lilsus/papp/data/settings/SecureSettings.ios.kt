package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

private const val SERVICE_NAME = "xyz.lilsus.papp.wallet"

@OptIn(ExperimentalSettingsImplementation::class)
actual fun createSecureSettings(): Settings = KeychainSettings(service = SERVICE_NAME)
