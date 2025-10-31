package xyz.lilsus.papp.data.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSCurrentLocaleDidChangeNotification
import platform.Foundation.NSLocale
import platform.Foundation.NSMutableArray
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.Foundation.arrayWithObject
import xyz.lilsus.papp.domain.model.LanguagePreference
import xyz.lilsus.papp.domain.repository.LanguageRepository

private const val OVERRIDE_KEY = "language.override.tag"
private const val APPLE_LANGUAGES_KEY = "AppleLanguages"

private class IosLanguageRepository : LanguageRepository {
    private val userDefaults = NSUserDefaults.standardUserDefaults()

    private val _preference = MutableStateFlow(currentPreference())
    @Suppress("unused")
    private val observer = NSNotificationCenter.defaultCenter.addObserverForName(
        name = NSCurrentLocaleDidChangeNotification,
        `object` = null,
        queue = null,
    ) { _ ->
        _preference.value = currentPreference()
    }

    override val preference: StateFlow<LanguagePreference> = _preference.asStateFlow()

    override suspend fun setLanguage(tag: String) {
        val array = NSMutableArray.arrayWithObject(tag)
        userDefaults.setObject(array, forKey = APPLE_LANGUAGES_KEY)
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
        _preference.value = currentPreference()
    }

    private fun notifyLocaleChanged() {
        NSNotificationCenter.defaultCenter.postNotificationName(NSCurrentLocaleDidChangeNotification, null)
        _preference.value = currentPreference()
    }

    private fun currentPreference(): LanguagePreference {
        val languages = currentPreferredLanguages()
        val resolvedTag = (languages.firstOrNull() as? String)?.ifBlank { "en" } ?: "en"
        val overrideTag = userDefaults.stringForKey(OVERRIDE_KEY)
        val deviceTag = deviceLanguageTag()
        return if (overrideTag != null && overrideTag.isNotBlank()) {
            LanguagePreference.Override(
                overrideTag = overrideTag,
                resolvedTag = resolvedTag.replace('_', '-'),
                deviceTag = deviceTag,
            )
        } else {
            LanguagePreference.System(resolvedTag = deviceTag)
        }
    }

    private fun deviceLanguageTag(): String = (currentPreferredLanguages().firstOrNull() as? String)
        ?.replace('_', '-')
        ?.takeIf { it.isNotBlank() }
        ?: "en"

    private fun currentPreferredLanguages(): List<Any> {
        val array = NSUserDefaults.standardUserDefaults().arrayForKey(APPLE_LANGUAGES_KEY)
        return array?.filterIsInstance<Any>() ?: emptyList()
    }
}

actual fun createLanguageRepository(): LanguageRepository = IosLanguageRepository()
