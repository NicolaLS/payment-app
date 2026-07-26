package xyz.lilsus.raylsuite.core.model

sealed interface DisplayCurrency {
    data object Bitcoin : DisplayCurrency

    data object Satoshi : DisplayCurrency

    data class Fiat(val iso4217: String) : DisplayCurrency
}

data class DisplayAmount(val minor: Long, val currency: DisplayCurrency)
