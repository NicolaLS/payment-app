package xyz.lilsus.raylsuite.feature.paymentcurrency

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider

class PaymentCurrencyManagerTest {
    @Test
    fun paymentQuoteUsesRateReturnedForThatAction() = runTest {
        var rate = 50_000.0
        val manager =
            PaymentCurrencyManager(
                bitcoinPriceProvider = BitcoinPriceProvider { rate },
                scope = backgroundScope
            )
        val enteredAmount = DisplayAmount(500, DisplayCurrency.Fiat("USD"))

        val first = manager.quote(enteredAmount)
        rate = 100_000.0
        val second = manager.quote(enteredAmount)

        assertEquals(enteredAmount, first?.requestedAmount)
        assertEquals(10_000_000L, first?.amountMsats)
        assertEquals(5_000_000L, second?.amountMsats)
        assertEquals(100_000.0, second?.exchangeRate)
    }

    @Test
    fun fiatQuoteFailsClosedWithoutRate() = runTest {
        val manager =
            PaymentCurrencyManager(
                bitcoinPriceProvider = BitcoinPriceProvider { null },
                scope = backgroundScope
            )

        assertNull(manager.quote(DisplayAmount(500, DisplayCurrency.Fiat("USD"))))
    }

    @Test
    fun unknownFiatCurrencyIsNotTreatedAsSatoshis() = runTest {
        val manager =
            PaymentCurrencyManager(
                bitcoinPriceProvider = BitcoinPriceProvider { 1.0 },
                scope = backgroundScope
            )

        assertNull(manager.quote(DisplayAmount(500, DisplayCurrency.Fiat("XYZ"))))
    }
}
