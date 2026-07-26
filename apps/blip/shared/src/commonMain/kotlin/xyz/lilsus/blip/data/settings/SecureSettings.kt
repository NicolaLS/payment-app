package xyz.lilsus.blip.data.settings

import com.russhwolf.settings.Settings

/**
 * Provides access to a platform-secure [Settings] instance backed by encrypted storage on Android
 * and Keychain on Apple platforms.
 */
expect fun createSecureSettings(): Settings
