package xyz.lilsus.papp.domain.model


sealed class DisplayCurrency {
    object Bitcoin : DisplayCurrency()
    object Satoshi : DisplayCurrency()
    data class Fiat(val iso4217: String) : DisplayCurrency()
}

data class DisplayAmount(
    val minor: Long,
    val currency: DisplayCurrency,
)