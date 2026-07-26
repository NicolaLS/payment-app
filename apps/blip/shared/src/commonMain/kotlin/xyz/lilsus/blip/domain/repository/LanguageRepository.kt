package xyz.lilsus.blip.domain.repository

import kotlinx.coroutines.flow.StateFlow
import xyz.lilsus.blip.domain.model.LanguagePreference

interface LanguageRepository {
    val preference: StateFlow<LanguagePreference>

    suspend fun setLanguage(tag: String)

    suspend fun clearOverride()

    suspend fun refresh()
}
