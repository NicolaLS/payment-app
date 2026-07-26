package xyz.lilsus.raylsuite.feature.languagesettings

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.raylsuite.core.model.LanguagePreference

private class AndroidLanguageRepository : LanguageRepository {
    private val mutablePreference =
        MutableStateFlow(readPreference(AppCompatDelegate.getApplicationLocales()))

    override val preference: StateFlow<LanguagePreference> = mutablePreference.asStateFlow()

    override suspend fun setLanguage(tag: String) {
        val current = mutablePreference.value
        if (
            current is LanguagePreference.Override &&
            current.overrideTag.equals(tag, ignoreCase = true)
        ) {
            return
        }

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        mutablePreference.value =
            readPreference(AppCompatDelegate.getApplicationLocales())
    }

    override suspend fun clearOverride() {
        if (mutablePreference.value is LanguagePreference.System) return

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        mutablePreference.value =
            readPreference(AppCompatDelegate.getApplicationLocales())
    }

    override suspend fun refresh() {
        mutablePreference.value =
            readPreference(AppCompatDelegate.getApplicationLocales())
    }

    private fun readPreference(locales: LocaleListCompat): LanguagePreference {
        val deviceTag = deviceLanguageTag()
        if (locales.isEmpty) {
            return LanguagePreference.System(resolvedTag = deviceTag)
        }

        val resolvedTag = locales[0]?.toLanguageTag() ?: deviceTag
        val overrideTag = locales.toLanguageTags().substringBefore(',')
        return LanguagePreference.Override(
            overrideTag = overrideTag.ifEmpty { resolvedTag },
            resolvedTag = resolvedTag,
            deviceTag = deviceTag
        )
    }

    private fun deviceLanguageTag(): String {
        val systemLocales = Resources.getSystem().configuration.locales
        return (systemLocales[0] ?: Locale.getDefault()).toLanguageTag()
    }
}

internal actual fun createPlatformLanguageRepository(): LanguageRepository =
    AndroidLanguageRepository()
