package xyz.lilsus.raylsuite.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogTest {
    @Test
    fun currencyLookupIsCaseInsensitive() {
        assertEquals(
            CurrencyInfo(
                code = "USD",
                currency = DisplayCurrency.Fiat("USD"),
                fractionDigits = 2
            ),
            CurrencyCatalog.infoFor("usd")
        )
    }

    @Test
    fun unknownCurrencyFallsBackToSatoshis() {
        assertEquals(
            CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE),
            CurrencyCatalog.infoFor("unknown")
        )
    }

    @Test
    fun languageLookupIsCaseInsensitive() {
        assertEquals(LanguageInfo("de", "de"), LanguageCatalog.infoForTag("DE"))
        assertNull(LanguageCatalog.infoForCode("fr"))
    }
}
