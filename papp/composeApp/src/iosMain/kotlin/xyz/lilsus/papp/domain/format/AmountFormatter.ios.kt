package xyz.lilsus.papp.domain.format

import platform.Foundation.*
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency

actual fun createAmountFormatter(locale: AppLocale): AmountFormatter = IosAmountFormatter(locale)

private class IosAmountFormatter(private val locale: AppLocale) : AmountFormatter {

    private val nsLocale = NSLocale(localeIdentifier = locale.bcp47)

    // Cache formatters to avoid expensive instantiation
    private val fiatFormatters = mutableMapOf<String, NSNumberFormatter>()
    private val bitcoinFormatter by lazy {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            minimumFractionDigits = 2u
            maximumFractionDigits = 8u
            locale = nsLocale
        }
    }
    private val satoshiFormatter by lazy {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            minimumFractionDigits = 0u
            maximumFractionDigits = 0u
            usesGroupingSeparator = true
            locale = nsLocale
        }
    }

    override fun format(amount: DisplayAmount): String = when (val currency = amount.currency) {
        is DisplayCurrency.Fiat -> formatFiat(amount.minor, currency)
        DisplayCurrency.Bitcoin -> formatBitcoin(amount.minor)
        DisplayCurrency.Satoshi -> formatSatoshi(amount.minor)
    }

    private fun formatFiat(minor: Long, currency: DisplayCurrency.Fiat): String {
        val code = currency.iso4217.uppercase()
        val formatter = fiatFormatters.getOrPut(code) {
            NSNumberFormatter().apply {
                numberStyle = NSNumberFormatterCurrencyStyle
                this.locale = nsLocale
                currencyCode = code
            }
        }
        val fractionDigitsNumber =
            (formatter.maximumFractionDigits as? NSNumber) ?: NSNumber(integer = 2)
        val fractionDigits = fractionDigitsNumber.intValue
        val decimalMinor = NSDecimalNumber(string = minor.toString())
        val major = decimalMinor.decimalNumberByMultiplyingByPowerOf10((-fractionDigits).toShort())
        return formatter.stringFromNumber(major) ?: "$minor $code"
    }

    private fun formatBitcoin(minor: Long): String {
        val decimalMinor = NSDecimalNumber(string = minor.toString())
        val btc = decimalMinor.decimalNumberByMultiplyingByPowerOf10((-8).toShort())
        val formatted = bitcoinFormatter.stringFromNumber(btc) ?: btc.stringValue
        return "$formatted BTC"
    }

    private fun formatSatoshi(minor: Long): String {
        val number = NSNumber(longLong = minor)
        val formatted = satoshiFormatter.stringFromNumber(number) ?: minor.toString()
        return "$formatted sat"
    }
}
