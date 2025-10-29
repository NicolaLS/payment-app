package xyz.lilsus.papp.domain.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency

actual fun createAmountFormatter(locale: AppLocale): AmountFormatter = AndroidAmountFormatter(locale)

private class AndroidAmountFormatter(private val locale: AppLocale) : AmountFormatter {

    private val javaLocale: Locale = locale.toLocale()

    override fun format(amount: DisplayAmount): String = when (val currency = amount.currency) {
        is DisplayCurrency.Fiat -> formatFiat(amount.minor, currency)
        DisplayCurrency.Bitcoin -> formatBitcoin(amount.minor)
        DisplayCurrency.Satoshi -> formatSatoshi(amount.minor)
    }

    private fun formatFiat(minor: Long, currency: DisplayCurrency.Fiat): String {
        val code = currency.iso4217.uppercase(Locale.ROOT)
        val formatter = NumberFormat.getCurrencyInstance(javaLocale)
        val javaCurrency = Currency.getInstance(code)
        val fractionDigits = javaCurrency.defaultFractionDigits.takeIf { it >= 0 } ?: 2
        formatter.currency = javaCurrency
        formatter.minimumFractionDigits = fractionDigits
        formatter.maximumFractionDigits = fractionDigits
        val major = BigDecimal.valueOf(minor, fractionDigits)
        return formatter.format(major)
    }

    private fun formatBitcoin(minor: Long): String {
        val formatter = NumberFormat.getNumberInstance(javaLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 8
        }
        val btc = BigDecimal.valueOf(minor, 8)
        val number = formatter.format(btc)
        return "$number BTC"
    }

    private fun formatSatoshi(minor: Long): String {
        val formatter = NumberFormat.getIntegerInstance(javaLocale)
        val number = formatter.format(minor)
        return "$number sat"
    }
}

private fun AppLocale.toLocale(): Locale {
    val candidate = Locale.forLanguageTag(languageTag)
    return if (candidate.language.isEmpty()) Locale.getDefault() else candidate
}
