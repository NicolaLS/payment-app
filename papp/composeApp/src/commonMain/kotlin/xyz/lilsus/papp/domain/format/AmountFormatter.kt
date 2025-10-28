package xyz.lilsus.papp.domain.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import xyz.lilsus.papp.domain.model.DisplayAmount

interface AmountFormatter {
    fun format(amount: DisplayAmount): String
}

expect class DefaultAmountFormatter(locale: AppLocale) : AmountFormatter

// TODO: Profiling test whether too many AmountFormatter's are being created.
@Composable
fun rememberAmountFormatter(locale: AppLocale? = null): AmountFormatter {
    val resolved = locale ?: runCatching { rememberAppLocale() }.getOrElse { currentAppLocale() }
    return remember(resolved) { DefaultAmountFormatter(resolved) }
}
