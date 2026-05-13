package xyz.lilsus.papp.data.settings

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import xyz.lilsus.papp.platform.AndroidAppContext

private const val PREF_NAME = "onboarding_settings"

/**
 * Creates regular (non-encrypted) Settings for onboarding using app-scoped SharedPreferences.
 */
actual fun createOnboardingSettings(): Settings {
    val context = AndroidAppContext.application.applicationContext
    return SharedPreferencesSettings(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    )
}
