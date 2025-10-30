package xyz.lilsus.papp.presentation.main.amount

import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import xyz.lilsus.papp.presentation.main.components.ManualAmountUiState

/**
 * Encapsulates the manual amount entry logic for the keypad sheet.
 */
class ManualAmountController(
    private val defaultCurrency: DisplayCurrency = DisplayCurrency.Satoshi,
) {
    private var digits: String = ""
    private var state: ManualAmountUiState = ManualAmountUiState(
        amount = null,
        currency = defaultCurrency,
        allowDecimal = defaultCurrency !is DisplayCurrency.Satoshi,
    )

    /**
     * Clears any user input and configures the controller for a new session.
     */
    fun reset(
        currency: DisplayCurrency = defaultCurrency,
        min: DisplayAmount? = null,
        max: DisplayAmount? = null,
        allowDecimal: Boolean = currency !is DisplayCurrency.Satoshi,
    ): ManualAmountUiState {
        digits = ""
        state = ManualAmountUiState(
            amount = null,
            currency = currency,
            min = min,
            max = max,
            allowDecimal = allowDecimal,
        )
        return state
    }

    /**
     * Returns the current UI representation without making changes.
     */
    fun current(): ManualAmountUiState = state

    /**
     * Applies the provided [key] and returns the updated UI state.
     */
    fun handleKeyPress(key: ManualAmountKey): ManualAmountUiState {
        when (key) {
            is ManualAmountKey.Digit -> appendDigit(key.value)
            ManualAmountKey.Backspace -> backspace()
            ManualAmountKey.Decimal -> {
                // Decimal input is currently unused for satoshi entry, so ignore.
            }
        }
        return state
    }

    /**
     * Returns the user-entered amount in millisatoshis, or null if none entered.
     */
    fun enteredAmountMsats(): Long? {
        val sats = digits.toLongOrNull() ?: return null
        if (sats > MAX_SATS) return null
        return sats * MSATS_PER_SAT
    }

    private fun appendDigit(digit: Int) {
        require(digit in 0..9) { "Digit must be between 0 and 9." }
        val appended = when {
            digits.isEmpty() -> digit.toString()
            digits == "0" -> if (digit == 0) "0" else digit.toString()
            else -> digits + digit
        }
        val normalized = if (appended.length > 1 && appended.startsWith("0")) {
            appended.dropWhile { it == '0' }.ifEmpty { "0" }
        } else {
            appended
        }
        val value = normalized.toLongOrNull() ?: return
        if (value > MAX_SATS) return
        digits = normalized
        updateAmount()
    }

    private fun backspace() {
        if (digits.isEmpty()) return
        digits = digits.dropLast(1)
        updateAmount()
    }

    private fun updateAmount() {
        val amount = digits.toLongOrNull()?.let {
            DisplayAmount(it, state.currency)
        }
        state = state.copy(amount = amount)
    }
}

private const val MSATS_PER_SAT = 1_000L
private const val MAX_SATS = Long.MAX_VALUE / MSATS_PER_SAT
