package xyz.lilsus.papp.data.settings

import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.papp.PappApplication
import xyz.lilsus.papp.domain.model.LanguagePreference
import xyz.lilsus.papp.domain.repository.LanguageRepository
import java.util.*

private class AndroidLanguageRepository : LanguageRepository {

    private val state = MutableStateFlow(readPreference(AppCompatDelegate.getApplicationLocales()))

    override val preference: StateFlow<LanguagePreference> = state.asStateFlow()

    override suspend fun setLanguage(tag: String) {
        val current = state.value
        if (current is LanguagePreference.Override && current.overrideTag.equals(tag, ignoreCase = true)) {
            return
        }
        val locales = LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)
        state.value = readPreference(AppCompatDelegate.getApplicationLocales())
        PappApplication.instance.recreateTopActivity()
    }

    override suspend fun clearOverride() {
        if (state.value is LanguagePreference.System) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        state.value = readPreference(AppCompatDelegate.getApplicationLocales())
        PappApplication.instance.recreateTopActivity()
    }

    override suspend fun refresh() {
        state.value = readPreference(AppCompatDelegate.getApplicationLocales())
    }

    private fun readPreference(locales: LocaleListCompat): LanguagePreference {
        val resolvedTag = locales[0]?.toLanguageTag()
            ?: Locale.getDefault().toLanguageTag()
        val deviceTag = deviceLanguageTag()
        return if (locales.isEmpty) {
            LanguagePreference.System(resolvedTag = deviceTag)
        } else {
            val overrideTag = locales.toLanguageTags().substringBefore(',')
            LanguagePreference.Override(
                overrideTag = overrideTag.ifEmpty { resolvedTag },
                resolvedTag = resolvedTag,
                deviceTag = deviceTag,
            )
        }
    }

    private fun deviceLanguageTag(): String {
        val systemLocales = Resources.getSystem().configuration.locales
        val locale = systemLocales[0] ?: Locale.getDefault()
        return locale.toLanguageTag()
    }
}

actual fun createLanguageRepository(): LanguageRepository = AndroidLanguageRepository()
