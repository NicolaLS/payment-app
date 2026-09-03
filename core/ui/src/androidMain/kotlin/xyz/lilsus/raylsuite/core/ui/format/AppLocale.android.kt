package xyz.lilsus.raylsuite.core.ui.format

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual fun currentAppLocale(): AppLocale {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val locale = appLocales[0] ?: Locale.getDefault()
    return AppLocale(locale.toLanguageTag())
}

@Composable
fun rememberAppLocale(): AppLocale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        currentAppLocale()
    }
}
