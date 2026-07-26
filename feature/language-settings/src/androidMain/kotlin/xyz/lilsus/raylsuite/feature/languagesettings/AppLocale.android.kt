package xyz.lilsus.raylsuite.feature.languagesettings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

internal actual fun currentAppLocale(): AppLocale {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val locale = appLocales[0] ?: Locale.getDefault()
    return AppLocale(locale.toLanguageTag())
}

@Composable
internal actual fun rememberAppLocale(): AppLocale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        currentAppLocale()
    }
}
