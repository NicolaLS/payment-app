package xyz.lilsus.papp.domain.model

import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.settings_currency_aud
import lasr.composeapp.generated.resources.settings_currency_bitcoin
import lasr.composeapp.generated.resources.settings_currency_cad
import lasr.composeapp.generated.resources.settings_currency_chf
import lasr.composeapp.generated.resources.settings_currency_eur
import lasr.composeapp.generated.resources.settings_currency_gbp
import lasr.composeapp.generated.resources.settings_currency_jpy
import lasr.composeapp.generated.resources.settings_currency_satoshi
import lasr.composeapp.generated.resources.settings_currency_usd
import org.jetbrains.compose.resources.StringResource

data class CurrencyInfo(
    val code: String,
    val currency: DisplayCurrency,
    val nameRes: StringResource,
    val fractionDigits: Int
)

object CurrencyCatalog {
    private val entries = listOf(
        CurrencyInfo(
            code = "SAT",
            currency = DisplayCurrency.Satoshi,
            nameRes = Res.string.settings_currency_satoshi,
            fractionDigits = 0
        ),
        CurrencyInfo(
            code = "BTC",
            currency = DisplayCurrency.Bitcoin,
            nameRes = Res.string.settings_currency_bitcoin,
            fractionDigits = 8
        ),
        CurrencyInfo(
            code = "USD",
            currency = DisplayCurrency.Fiat("USD"),
            nameRes = Res.string.settings_currency_usd,
            fractionDigits = 2
        ),
        CurrencyInfo(
            code = "EUR",
            currency = DisplayCurrency.Fiat("EUR"),
            nameRes = Res.string.settings_currency_eur,
            fractionDigits = 2
        ),
        CurrencyInfo(
            code = "GBP",
            currency = DisplayCurrency.Fiat("GBP"),
            nameRes = Res.string.settings_currency_gbp,
            fractionDigits = 2
        ),
        CurrencyInfo(
            code = "CAD",
            currency = DisplayCurrency.Fiat("CAD"),
            nameRes = Res.string.settings_currency_cad,
            fractionDigits = 2
        ),
        CurrencyInfo(
            code = "AUD",
            currency = DisplayCurrency.Fiat("AUD"),
            nameRes = Res.string.settings_currency_aud,
            fractionDigits = 2
        ),
        CurrencyInfo(
            code = "CHF",
            currency = DisplayCurrency.Fiat("CHF"),
            nameRes = Res.string.settings_currency_chf,
            fractionDigits = 2
        ),
        CurrencyInfo(
            code = "JPY",
            currency = DisplayCurrency.Fiat("JPY"),
            nameRes = Res.string.settings_currency_jpy,
            fractionDigits = 0
        )
    )

    private val byCode = entries.associateBy { it.code.uppercase() }

    val supportedCodes: List<String> = entries.map { it.code }

    fun infoFor(code: String): CurrencyInfo = byCode[code.uppercase()]
        ?: byCode.getValue(DEFAULT_CODE)

    fun infoFor(currency: DisplayCurrency): CurrencyInfo = when (currency) {
        DisplayCurrency.Satoshi -> infoFor("SAT")
        DisplayCurrency.Bitcoin -> infoFor("BTC")
        is DisplayCurrency.Fiat -> infoFor(currency.iso4217)
    }

    const val DEFAULT_CODE = "SAT"
}
