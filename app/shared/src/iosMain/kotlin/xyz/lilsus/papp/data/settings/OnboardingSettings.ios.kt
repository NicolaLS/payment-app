package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

/**
 * Creates regular (non-encrypted) Settings for onboarding using NSUserDefaults.
 * Note: NSUserDefaults may persist after app uninstall on iOS, but wallet
 * credentials (stored in Keychain) also persist, so the behavior is consistent.
 */
actual fun createOnboardingSettings(): Settings =
    NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
