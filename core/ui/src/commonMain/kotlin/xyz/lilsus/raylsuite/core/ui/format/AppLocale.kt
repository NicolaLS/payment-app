package xyz.lilsus.raylsuite.core.ui.format

import androidx.compose.runtime.Composable

data class AppLocale(val languageTag: String)

expect fun currentAppLocale(): AppLocale

@Composable
expect fun rememberAppLocale(): AppLocale
