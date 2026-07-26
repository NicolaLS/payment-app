package xyz.lilsus.raylsuite.feature.languagesettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import xyz.lilsus.raylsuite.core.model.LanguagePreference

interface LanguageRepository {
    val preference: StateFlow<LanguagePreference>

    suspend fun setLanguage(tag: String)

    suspend fun clearOverride()

    suspend fun refresh()
}

internal expect fun createPlatformLanguageRepository(): LanguageRepository

@Composable
fun rememberLanguageRepository(): LanguageRepository = remember {
    createPlatformLanguageRepository()
}
