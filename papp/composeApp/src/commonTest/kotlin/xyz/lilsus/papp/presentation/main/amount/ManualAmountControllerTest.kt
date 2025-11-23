package xyz.lilsus.papp.presentation.main.amount

import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import xyz.lilsus.papp.presentation.main.components.RangeStatus
import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun reportsBelowMinWhenUnderRange() {
        val controller = ManualAmountController(
            usdConfig.copy(
                min = usdDisplay(100), // $1.00
                max = usdDisplay(500), // $5.00
                minMsats = 1_500_000L,
                maxMsats = 9_000_000L,
            )
        )

        controller.handleKeyPress(ManualAmountKey.Decimal)
        val state = controller.handleKeyPress(ManualAmountKey.Digit(5)) // 0.5 USD

        assertEquals(RangeStatus.BelowMin(usdDisplay(100)), state.rangeStatus)
    }

    @Test
    fun presetAmountUsesRangeAndFraction() {
        val controller = ManualAmountController(usdConfig)

        val state = controller.presetAmount(usdDisplay(1234)) // $12.34

        assertEquals("12", state.rawWhole)
        assertEquals("34", state.rawFraction)
        assertEquals(RangeStatus.InRange, state.rangeStatus)
    }

    @Test
    fun presetWholeAmountDoesNotShowGhostDecimal() {
        val controller = ManualAmountController(usdConfig)

        val state = controller.presetAmount(usdDisplay(8_666_900)) // $86,669.00

        assertFalse(state.hasDecimal)
        assertEquals("", state.rawFraction)
    }

    private fun usdDisplay(minor: Long) = xyz.lilsus.papp.domain.model.DisplayAmount(
        minor = minor,
        currency = usdConfig.info.currency,
    )
}
