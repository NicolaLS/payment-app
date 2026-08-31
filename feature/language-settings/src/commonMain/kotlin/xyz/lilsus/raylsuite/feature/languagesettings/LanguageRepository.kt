package xyz.lilsus.raylsuite.feature.languagesettings

import kotlinx.coroutines.flow.StateFlow
import xyz.lilsus.raylsuite.core.model.LanguagePreference

interface LanguageRepository {
    val preference: StateFlow<LanguagePreference>

    suspend fun setLanguage(tag: String)

    suspend fun clearOverride()

    suspend fun refresh()

    fun close()
}

internal expect fun createPlatformLanguageRepository(): LanguageRepository

fun createLanguageRepository(): LanguageRepository = createPlatformLanguageRepository()
