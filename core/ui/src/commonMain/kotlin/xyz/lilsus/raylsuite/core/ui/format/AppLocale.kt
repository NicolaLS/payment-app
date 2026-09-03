package xyz.lilsus.raylsuite.core.ui.format

data class AppLocale(val languageTag: String)

expect fun currentAppLocale(): AppLocale
