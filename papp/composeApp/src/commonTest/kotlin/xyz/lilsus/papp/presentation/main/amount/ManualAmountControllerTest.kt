package xyz.lilsus.papp.presentation.main.amount

import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualAmountControllerTest {

    private val usdConfig = ManualAmountConfig(
        info = CurrencyCatalog.infoFor("USD"),
        exchangeRate = 60_000.0,
    )

    @Test
    fun showsDecimalPlaceholderWhenFractionEmpty() {
        val controller = ManualAmountController(usdConfig)

        val state = controller.handleKeyPress(ManualAmountKey.Decimal)

        assertTrue(state.hasDecimal)
        assertEquals("0", state.rawWhole)
        assertEquals("", state.rawFraction)
        assertNull(state.amount)
    }

    @Test
    fun locksFractionDigitAfterTyping() {
        val controller = ManualAmountController(usdConfig)

        controller.handleKeyPress(ManualAmountKey.Decimal)
        val state = controller.handleKeyPress(ManualAmountKey.Digit(1))

        assertTrue(state.hasDecimal)
        assertEquals("0", state.rawWhole)
        assertEquals("1", state.rawFraction)
        assertNotNull(state.amount)
        assertEquals(10L, state.amount?.minor)
    }
}
