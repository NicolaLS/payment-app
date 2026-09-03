package xyz.lilsus.raylsuite.feature.appshell

import xyz.lilsus.raylsuite.core.model.ThemePreference

fun ThemePreference.nativeColorSchemeValue(): String = when (this) {
    ThemePreference.System -> "system"
    ThemePreference.Light -> "light"
    ThemePreference.Dark -> "dark"
}
