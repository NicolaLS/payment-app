package xyz.lilsus.raylsuite.feature.languagesettings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import xyz.lilsus.raylsuite.core.model.LanguagePreference

/** Observes the language selected by iOS; application code never writes Apple's preferences. */
private class IosSystemLanguageRepository : LanguageRepository {
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val mutablePreference: MutableStateFlow<LanguagePreference>

    private val activeObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = null
        ) {
            mutablePreference.value = currentPreference()
        }

    init {
        removeLegacyApplicationOverride()
        mutablePreference = MutableStateFlow(currentPreference())
    }

    override val preference: StateFlow<LanguagePreference> = mutablePreference.asStateFlow()
    override val management: LanguageManagement = LanguageManagement.SystemSettings

    override suspend fun setLanguage(tag: String) {
        error("The iOS app language is managed by system Settings")
    }

    override suspend fun clearOverride() {
        error("The iOS app language is managed by system Settings")
    }

    override suspend fun refresh() {
        mutablePreference.value = currentPreference()
    }

    override fun close() {
        NSNotificationCenter.defaultCenter.removeObserver(activeObserver)
    }

    private fun currentPreference(): LanguagePreference {
        val resolvedTag =
            NSBundle.mainBundle.preferredLocalizations
                .firstOrNull()
                ?.toString()
                ?.replace('_', '-')
                ?.takeIf(String::isNotBlank)
                ?: "en"
        return LanguagePreference.System(resolvedTag = resolvedTag)
    }

    private fun removeLegacyApplicationOverride() {
        if (userDefaults.stringForKey(LEGACY_OVERRIDE_KEY) == null) return
        userDefaults.removeObjectForKey(LEGACY_OVERRIDE_KEY)
        userDefaults.removeObjectForKey(LEGACY_APPLE_LANGUAGES_KEY)
    }

    private companion object {
        const val LEGACY_OVERRIDE_KEY = "localization.selectedLanguage"
        const val LEGACY_APPLE_LANGUAGES_KEY = "AppleLanguages"
    }
}

internal actual fun createPlatformLanguageRepository(): LanguageRepository =
    IosSystemLanguageRepository()
