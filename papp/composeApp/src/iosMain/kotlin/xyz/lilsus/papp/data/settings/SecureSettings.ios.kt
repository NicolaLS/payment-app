package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

private const val SERVICE_NAME = "xyz.lilsus.papp.wallet"

actual fun createSecureSettings(): Settings {
    return KeychainSettings(service = SERVICE_NAME)
}
