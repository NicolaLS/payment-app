package xyz.lilsus.papp.domain.format

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency

actual fun createAmountFormatter(locale: AppLocale): AmountFormatter =
    AndroidAmountFormatter(locale)

private class AndroidAmountFormatter(private val locale: AppLocale) : AmountFormatter {

    private val javaLocale: Locale = locale.toLocale()

    // Cache formatters to avoid expensive instantiation
    private val fiatFormatters = mutableMapOf<String, NumberFormat>()
    private val bitcoinFormatter by lazy {
        NumberFormat.getNumberInstance(javaLocale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 8
        }
    }
    private val satoshiFormatter by lazy {
        NumberFormat.getIntegerInstance(javaLocale)
    }

    override fun format(amount: DisplayAmount): String = when (val currency = amount.currency) {
        is DisplayCurrency.Fiat -> formatFiat(amount.minor, currency)
        DisplayCurrency.Bitcoin -> formatBitcoin(amount.minor)
        DisplayCurrency.Satoshi -> formatSatoshi(amount.minor)
    }

    private fun formatFiat(minor: Long, currency: DisplayCurrency.Fiat): String {
        val code = currency.iso4217.uppercase(Locale.ROOT)
        val formatter = fiatFormatters.getOrPut(code) {
            NumberFormat.getCurrencyInstance(javaLocale).apply {
                val javaCurrency = Currency.getInstance(code)
                this.currency = javaCurrency
                val fractionDigits = javaCurrency.defaultFractionDigits.takeIf { it >= 0 } ?: 2
                minimumFractionDigits = fractionDigits
                maximumFractionDigits = fractionDigits
            }
        }
        val fractionDigits = formatter.currency?.defaultFractionDigits ?: 2
        val major = BigDecimal.valueOf(minor, fractionDigits)
        return formatter.format(major)
    }

    private fun formatBitcoin(minor: Long): String {
        val btc = BigDecimal.valueOf(minor, 8)
        val number = bitcoinFormatter.format(btc)
        return "$number BTC"
    }

    private fun formatSatoshi(minor: Long): String {
        val number = satoshiFormatter.format(minor)
        return "$number sat"
    }
}

private fun AppLocale.toLocale(): Locale {
    val candidate = Locale.forLanguageTag(languageTag)
    return if (candidate.language.isEmpty()) Locale.getDefault() else candidate
}
