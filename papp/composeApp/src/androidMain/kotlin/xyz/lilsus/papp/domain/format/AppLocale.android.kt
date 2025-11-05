package xyz.lilsus.papp.domain.format

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.util.*

actual fun currentAppLocale(): AppLocale {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val primary = if (!appLocales.isEmpty) appLocales[0] else null
    val locale = primary ?: Locale.getDefault()
    return AppLocale(locale.toLanguageTag())
}

// TODO: Check whether reading configuration here is actually necessary for cases where user
// navigates to settings, changes locale and goes back to screen. I tested setting the app language
// and go back to screen, which triggers recomposition even without using configuration/remember.
// But it might be necessary for when user sets lang in the app settings (AppCompatDeligate)
@Composable
actual fun rememberAppLocale(): AppLocale {
    val configuration = LocalConfiguration.current
    return remember(configuration) { currentAppLocale() }
}
