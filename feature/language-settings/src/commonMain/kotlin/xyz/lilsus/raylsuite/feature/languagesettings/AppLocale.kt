package xyz.lilsus.raylsuite.feature.languagesettings

import androidx.compose.runtime.Composable

data class AppLocale(val languageTag: String)

internal expect fun currentAppLocale(): AppLocale

@Composable
internal expect fun rememberAppLocale(): AppLocale
