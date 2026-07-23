package xyz.lilsus.rayl.blip.domain

import fr.acinq.lightning.MilliSatoshi
import kotlin.jvm.JvmInline

@JvmInline
value class CurrencyCode private constructor(val value: String) {
    companion object {
        fun parse(value: String): CurrencyCode? = value.trim().uppercase()
            .takeIf { it in SUPPORTED_CURRENCIES }
            ?.let(::CurrencyCode)

        val Sat = require("SAT")
        val Btc = require("BTC")

        fun require(value: String): CurrencyCode =
            requireNotNull(parse(value)) { "Unsupported currency" }

        val supported: List<CurrencyCode> =
            SUPPORTED_CURRENCIES.map(::CurrencyCode)
    }
}

data class ExchangeRateSnapshot(
    val quote: CurrencyCode,
    val microsPerBitcoin: Long,
    val fetchedAtMillis: Long
) {
    init {
        require(quote !in setOf(CurrencyCode.Sat, CurrencyCode.Btc))
        require(microsPerBitcoin > 0L)
    }
}

interface ExchangeRates {
    suspend fun snapshot(quote: CurrencyCode): ExchangeRateSnapshot?

    suspend fun toMilliSatoshi(value: String, currency: CurrencyCode): ConvertedAmount?

    fun format(
        amount: MilliSatoshi,
        currency: CurrencyCode,
        snapshot: ExchangeRateSnapshot?
    ): String?
}

data class ConvertedAmount(val amount: MilliSatoshi, val rateSnapshot: ExchangeRateSnapshot?)

internal const val MSAT_PER_BITCOIN = 100_000_000_000L
internal const val MSAT_PER_SAT = 1_000L

private val SUPPORTED_CURRENCIES =
    listOf("SAT", "BTC", "USD", "EUR", "GBP", "CAD", "AUD", "CHF", "JPY")
