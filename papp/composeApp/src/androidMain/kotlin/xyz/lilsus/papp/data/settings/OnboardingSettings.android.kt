package xyz.lilsus.papp.data.settings

import android.content.Context
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import xyz.lilsus.papp.PappApplication

private const val PREF_NAME = "onboarding_settings"

/**
 * Creates regular (non-encrypted) Settings for onboarding using SharedPreferences.
 * This data will be deleted when the app is uninstalled.
 */
actual fun createOnboardingSettings(): Settings {
    val context = PappApplication.instance.applicationContext
    return SharedPreferencesSettings(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    )
}
