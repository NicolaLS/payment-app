package xyz.lilsus.papp.domain.format

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency

actual class DefaultAmountFormatter actual constructor(private val locale: AppLocale) : AmountFormatter {

    private val nsLocale = NSLocale(localeIdentifier = locale.bcp47)

    override fun format(amount: DisplayAmount): String = when (val currency = amount.currency) {
        is DisplayCurrency.Fiat -> formatFiat(amount.minor, currency)
        DisplayCurrency.Bitcoin -> formatBitcoin(amount.minor)
        DisplayCurrency.Satoshi -> formatSatoshi(amount.minor)
    }

    private fun formatFiat(minor: Long, currency: DisplayCurrency.Fiat): String {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            this.locale = nsLocale
            currencyCode = currency.iso4217.uppercase()
        }
        val fractionDigitsNumber =
            (formatter.maximumFractionDigits as? NSNumber) ?: NSNumber(integer = 2)
        val fractionDigits = fractionDigitsNumber.intValue
        val decimalMinor = NSDecimalNumber(string = minor.toString())
        val major = decimalMinor.decimalNumberByMultiplyingByPowerOf10((-fractionDigits).toShort())
        return formatter.stringFromNumber(major) ?: "${minor} ${currency.iso4217.uppercase()}"
    }

    private fun formatBitcoin(minor: Long): String {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            minimumFractionDigits = 2u
            maximumFractionDigits = 8u
            locale = nsLocale
        }
        val decimalMinor = NSDecimalNumber(string = minor.toString())
        val btc = decimalMinor.decimalNumberByMultiplyingByPowerOf10((-8).toShort())
        val formatted = formatter.stringFromNumber(btc) ?: btc.stringValue
        return "$formatted BTC"
    }

    private fun formatSatoshi(minor: Long): String {
        val formatter = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            minimumFractionDigits = 0u
            maximumFractionDigits = 0u
            usesGroupingSeparator = true
            locale = nsLocale
        }
        val number = NSNumber(longLong = minor)
        val formatted = formatter.stringFromNumber(number) ?: minor.toString()
        return "$formatted sat"
    }
}
