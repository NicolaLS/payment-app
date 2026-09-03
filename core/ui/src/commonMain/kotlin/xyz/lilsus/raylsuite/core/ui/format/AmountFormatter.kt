package xyz.lilsus.raylsuite.core.ui.format

import xyz.lilsus.raylsuite.core.model.DisplayAmount

interface AmountFormatter {
    fun format(amount: DisplayAmount): String
}

internal expect fun createAmountFormatter(locale: AppLocale): AmountFormatter

/** Creates an amount formatter for presentation code that is not running in composition. */
fun currentAmountFormatter(locale: AppLocale = currentAppLocale()): AmountFormatter =
    createAmountFormatter(locale)
