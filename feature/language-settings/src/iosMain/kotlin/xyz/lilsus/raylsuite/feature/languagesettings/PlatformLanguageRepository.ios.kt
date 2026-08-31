package xyz.lilsus.raylsuite.feature.languagesettings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSMutableArray
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.arrayWithObject
import xyz.lilsus.raylsuite.core.model.LanguagePreference

private class IosLanguageRepository : LanguageRepository {
    private val userDefaults = NSUserDefaults.standardUserDefaults
    private val mutablePreference = MutableStateFlow(currentPreference())

    private val localeObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSCurrentLocaleDidChangeNotification,
            `object` = null,
            queue = null
        ) {
            mutablePreference.value = currentPreference()
        }

    override val preference: StateFlow<LanguagePreference> = mutablePreference.asStateFlow()

    override suspend fun setLanguage(tag: String) {
        userDefaults.setObject(
            NSMutableArray.arrayWithObject(tag),
            forKey = APPLE_LANGUAGES_KEY
        )
        userDefaults.setObject(tag, forKey = OVERRIDE_KEY)
        userDefaults.synchronize()
        notifyLocaleChanged()
    }

    override suspend fun clearOverride() {
        userDefaults.removeObjectForKey(OVERRIDE_KEY)
        userDefaults.removeObjectForKey(APPLE_LANGUAGES_KEY)
        userDefaults.synchronize()
        notifyLocaleChanged()
    }

    override suspend fun refresh() {
        mutablePreference.value = currentPreference()
    }

    override fun close() {
        NSNotificationCenter.defaultCenter.removeObserver(localeObserver)
    }

    private fun notifyLocaleChanged() {
        NSNotificationCenter.defaultCenter.postNotificationName(
            NSCurrentLocaleDidChangeNotification,
            null
        )
        mutablePreference.value = currentPreference()
    }

    private fun currentPreference(): LanguagePreference {
        val resolvedTag = currentPreferredLanguages().firstOrNull()?.ifBlank { "en" } ?: "en"
        val overrideTag = userDefaults.stringForKey(OVERRIDE_KEY)
        val deviceTag = deviceLanguageTag()
        return if (overrideTag.isNullOrBlank()) {
            LanguagePreference.System(resolvedTag = deviceTag)
        } else {
            LanguagePreference.Override(
                overrideTag = overrideTag,
                resolvedTag = resolvedTag.replace('_', '-'),
                deviceTag = deviceTag
            )
        }
    }

    private fun deviceLanguageTag(): String = currentPreferredLanguages()
        .firstOrNull()
        ?.replace('_', '-')
        ?.takeIf(String::isNotBlank)
        ?: "en"

    private fun currentPreferredLanguages(): List<String> = userDefaults
        .arrayForKey(APPLE_LANGUAGES_KEY)
        ?.filterIsInstance<String>()
        .orEmpty()

    private companion object {
        const val OVERRIDE_KEY = "localization.selectedLanguage"
        const val APPLE_LANGUAGES_KEY = "AppleLanguages"
    }
}

internal actual fun createPlatformLanguageRepository(): LanguageRepository = IosLanguageRepository()
