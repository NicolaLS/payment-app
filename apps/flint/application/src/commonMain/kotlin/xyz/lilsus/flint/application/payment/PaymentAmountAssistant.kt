package xyz.lilsus.flint.application.payment

import kotlin.math.roundToLong
import xyz.lilsus.raylsuite.core.model.Satoshi
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider

interface PaymentAmountAssistant : BitcoinPriceProvider {
    suspend fun quoteFiatAmount(amount: FiatMinorAmount): FiatAmountQuoteResult
}

data class FiatCurrency(val code: String, val name: String, val fractionDigits: Int) {
    init {
        require(isFiatCode(code) && code == normalizeCurrencyCode(code))
        require(name.isNotBlank())
        require(fractionDigits in 0..MAX_FIAT_FRACTION_DIGITS)
    }
}

data class FiatMinorAmount(val currencyCode: String, val minorUnits: Long) {
    init {
        require(isFiatCode(currencyCode) && currencyCode == normalizeCurrencyCode(currencyCode))
        require(minorUnits > 0)
    }
}

data class FiatRateSnapshot(
    val currencyCode: String,
    val pricePerBitcoin: Double,
    val observedAtEpochSeconds: Long
) {
    init {
        require(isFiatCode(currencyCode) && currencyCode == normalizeCurrencyCode(currencyCode))
        require(pricePerBitcoin.isFinite() && pricePerBitcoin > 0.0)
        require(observedAtEpochSeconds >= 0)
    }
}

data class FiatAmountQuote(
    val input: FiatMinorAmount,
    val currency: FiatCurrency,
    val sats: Satoshi,
    val rate: FiatRateSnapshot
) {
    init {
        require(input.currencyCode == currency.code)
        require(rate.currencyCode == currency.code)
        require(
            convertFiatMinorUnitsToSats(
                input.minorUnits,
                currency.fractionDigits,
                rate.pricePerBitcoin
            ) == sats.value
        )
    }
}

sealed interface FiatAmountQuoteResult {
    data class Quoted(val quote: FiatAmountQuote) : FiatAmountQuoteResult
    data object WalletUnavailable : FiatAmountQuoteResult
    data object CurrencyUnavailable : FiatAmountQuoteResult
    data object RateUnavailable : FiatAmountQuoteResult
    data object InvalidAmount : FiatAmountQuoteResult
}

data class SdkFiatCurrency(val code: String, val name: String, val fractionDigits: Int)

data class SdkFiatRate(val code: String, val pricePerBitcoin: Double)

data class SdkFiatMarket(val currencies: List<SdkFiatCurrency>, val rates: List<SdkFiatRate>)

data class FiatMarketSnapshot(
    val currencies: List<FiatCurrency>,
    val rates: Map<String, Double>,
    val observedAtEpochSeconds: Long
) {
    fun quote(amount: FiatMinorAmount): FiatAmountQuoteResult {
        val currency = currencies.firstOrNull { it.code == amount.currencyCode }
            ?: return FiatAmountQuoteResult.CurrencyUnavailable
        val rate = rates[amount.currencyCode] ?: return FiatAmountQuoteResult.RateUnavailable
        val rounded = convertFiatMinorUnitsToSats(amount.minorUnits, currency.fractionDigits, rate)
            ?: return FiatAmountQuoteResult.InvalidAmount
        return FiatAmountQuoteResult.Quoted(
            FiatAmountQuote(
                input = amount,
                currency = currency,
                sats = Satoshi.positive(rounded),
                rate = FiatRateSnapshot(amount.currencyCode, rate, observedAtEpochSeconds)
            )
        )
    }
}

fun normalizeCurrencyCode(code: String): String = code.trim().uppercase()

private fun isFiatCode(code: String): Boolean =
    code.length == 3 && code.all { it in 'A'..'Z' } && code !in setOf(SAT, BTC)

private fun convertFiatMinorUnitsToSats(
    minorUnits: Long,
    fractionDigits: Int,
    pricePerBitcoin: Double
): Long? {
    val divisor = POWERS_OF_TEN[fractionDigits].toDouble()
    val sats = minorUnits.toDouble() / divisor / pricePerBitcoin * SATS_PER_BITCOIN.toDouble()
    if (!sats.isFinite() || sats < 0.5 || sats >= Long.MAX_VALUE.toDouble()) return null
    return sats.roundToLong().takeIf { it > 0 }
}

private const val SAT = "SAT"
private const val BTC = "BTC"
private const val MAX_FIAT_FRACTION_DIGITS = 8
private const val SATS_PER_BITCOIN = 100_000_000L
private val POWERS_OF_TEN = longArrayOf(
    1,
    10,
    100,
    1_000,
    10_000,
    100_000,
    1_000_000,
    10_000_000,
    100_000_000
)
