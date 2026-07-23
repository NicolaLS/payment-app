package xyz.lilsus.rayl.foundation.ui.domain.model

import kotlin.math.pow
import kotlin.math.roundToLong

fun convertMsatsToDisplayAmount(
    msats: Long,
    info: CurrencyInfo,
    fiatPricePerBitcoin: Double?
): DisplayAmount? = when (val currency = info.currency) {
    DisplayCurrency.Satoshi -> DisplayAmount(msats / MSATS_PER_SAT, currency)

    DisplayCurrency.Bitcoin -> DisplayAmount(msats / MSATS_PER_SAT, currency)

    is DisplayCurrency.Fiat -> {
        val rate = fiatPricePerBitcoin ?: return null
        val btc = msats.toDouble() / MSATS_PER_BTC
        val fiatMajor = btc * rate
        val factor = 10.0.pow(info.fractionDigits)
        val minor = (fiatMajor * factor).roundToLong()
        val clamped = if (minor <= 0 && msats > 0) 1 else minor
        DisplayAmount(clamped, currency)
    }
}

private const val MSATS_PER_SAT = 1_000L
private const val MSATS_PER_BTC = 100_000_000_000L
