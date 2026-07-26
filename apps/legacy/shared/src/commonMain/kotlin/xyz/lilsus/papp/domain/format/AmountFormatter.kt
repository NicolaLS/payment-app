package xyz.lilsus.papp.domain.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import xyz.lilsus.papp.domain.model.DisplayAmount

interface AmountFormatter {
    fun format(amount: DisplayAmount): String
}

expect fun createAmountFormatter(locale: AppLocale): AmountFormatter

@Composable
fun rememberAmountFormatter(locale: AppLocale? = null): AmountFormatter {
    val resolved = locale ?: rememberAppLocale()
    return remember(resolved) { createAmountFormatter(resolved) }
}
