package xyz.lilsus.papp.domain.model.exchange

/** Represents the price of one bitcoin in a given currency. */
data class ExchangeRate(val currencyCode: String, val pricePerBitcoin: Double)
