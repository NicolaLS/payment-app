package xyz.lilsus.raylsuite.core.payment

fun interface BitcoinPriceProvider {
    suspend fun pricePerBitcoin(fiatCurrencyCode: String): Double?
}
