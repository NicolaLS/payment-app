package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.StateFlow
import xyz.lilsus.papp.domain.model.LanguagePreference

interface LanguageRepository {
    val preference: StateFlow<LanguagePreference>

    suspend fun setLanguage(tag: String)

    suspend fun clearOverride()

    suspend fun refresh()
}
