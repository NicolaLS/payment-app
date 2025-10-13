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
fun rememberAmountFormatter(locale: AppLocale = rememberAppLocale()): AmountFormatter {
    return remember(locale) { DefaultAmountFormatter(locale) }
}
