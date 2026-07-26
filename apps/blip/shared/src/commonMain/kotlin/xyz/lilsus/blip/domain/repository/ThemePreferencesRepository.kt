package xyz.lilsus.blip.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.blip.domain.model.ThemePreference

interface ThemePreferencesRepository {
    val preference: Flow<ThemePreference>

    suspend fun getThemePreference(): ThemePreference

    suspend fun setThemePreference(preference: ThemePreference)
}
