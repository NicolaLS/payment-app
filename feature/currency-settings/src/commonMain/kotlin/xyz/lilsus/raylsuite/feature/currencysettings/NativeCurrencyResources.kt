package xyz.lilsus.raylsuite.feature.currencysettings

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.Res
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.search_placeholder
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_aud
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_bitcoin
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_cad
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_chf
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_eur
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_gbp
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_jpy
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_satoshi
import xyz.lilsus.raylsuite.feature.currencysettings.generated.resources.settings_currency_usd

suspend fun nativeCurrencyStrings(): Map<String, String> = mapOf(
    "search" to getString(Res.string.search_placeholder),
    "SAT" to getString(Res.string.settings_currency_satoshi),
    "BTC" to getString(Res.string.settings_currency_bitcoin),
    "USD" to getString(Res.string.settings_currency_usd),
    "EUR" to getString(Res.string.settings_currency_eur),
    "GBP" to getString(Res.string.settings_currency_gbp),
    "CAD" to getString(Res.string.settings_currency_cad),
    "AUD" to getString(Res.string.settings_currency_aud),
    "CHF" to getString(Res.string.settings_currency_chf),
    "JPY" to getString(Res.string.settings_currency_jpy)
)
