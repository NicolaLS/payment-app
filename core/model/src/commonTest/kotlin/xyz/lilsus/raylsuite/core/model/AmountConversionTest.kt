package xyz.lilsus.raylsuite.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AmountConversionTest {
    @Test
    fun convertsMsatsToSatoshis() {
        val amount =
            convertMsatsToDisplayAmount(
                msats = 12_345_000,
                info = CurrencyCatalog.infoFor("SAT"),
                fiatPricePerBitcoin = null
            )

        assertEquals(
            DisplayAmount(
                minor = 12_345,
                currency = DisplayCurrency.Satoshi
            ),
            amount
        )
    }

    @Test
    fun convertsMsatsToFiatMinorUnits() {
        val amount =
            convertMsatsToDisplayAmount(
                msats = 50_000_000_000,
                info = CurrencyCatalog.infoFor("USD"),
                fiatPricePerBitcoin = 60_000.0
            )

        assertEquals(
            DisplayAmount(
                minor = 3_000_000,
                currency = DisplayCurrency.Fiat("USD")
            ),
            amount
        )
    }

    @Test
    fun requiresPriceForFiatConversion() {
        assertNull(
            convertMsatsToDisplayAmount(
                msats = 1_000,
                info = CurrencyCatalog.infoFor("EUR"),
                fiatPricePerBitcoin = null
            )
        )
    }

    @Test
    fun preservesSmallPositiveFiatAmounts() {
        val amount =
            convertMsatsToDisplayAmount(
                msats = 1_000,
                info = CurrencyCatalog.infoFor("USD"),
                fiatPricePerBitcoin = 1.0
            )

        assertEquals(1, amount?.minor)
    }
}
