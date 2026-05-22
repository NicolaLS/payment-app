package xyz.lilsus.papp.domain.format

import androidx.compose.runtime.Composable

data class AppLocale(val bcp47: String) {
    val languageTag: String get() = bcp47
}

expect fun currentAppLocale(): AppLocale

@Composable
expect fun rememberAppLocale(): AppLocale
