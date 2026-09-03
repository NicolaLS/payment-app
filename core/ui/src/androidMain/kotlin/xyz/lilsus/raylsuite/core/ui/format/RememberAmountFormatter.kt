package xyz.lilsus.raylsuite.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun rememberAmountFormatter(locale: AppLocale? = null): AmountFormatter {
    val resolvedLocale = locale ?: rememberAppLocale()
    return remember(resolvedLocale) {
        createAmountFormatter(resolvedLocale)
    }
}
