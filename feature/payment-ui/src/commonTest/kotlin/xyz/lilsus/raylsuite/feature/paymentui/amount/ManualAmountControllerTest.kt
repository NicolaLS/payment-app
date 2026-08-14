package xyz.lilsus.raylsuite.feature.paymentui.amount

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.DisplayAmount

class ManualAmountControllerTest {
    @Test
    fun reportsBelowMinWhenUnderRange() {
        val usd = CurrencyCatalog.infoFor("USD")
        val controller =
            ManualAmountController(
                ManualAmountConfig(
                    info = usd,
                    exchangeRate = 60_000.0,
                    min = DisplayAmount(100, usd.currency),
                    max = DisplayAmount(500, usd.currency),
                    minMsats = 1_500_000L,
                    maxMsats = 9_000_000L
                )
            )

        controller.handleKeyPress(ManualAmountKey.Decimal)
        val state = controller.handleKeyPress(ManualAmountKey.Digit(5))

        assertEquals(
            RangeStatus.BelowMin(DisplayAmount(100, usd.currency)),
            state.rangeStatus
        )
    }
}
