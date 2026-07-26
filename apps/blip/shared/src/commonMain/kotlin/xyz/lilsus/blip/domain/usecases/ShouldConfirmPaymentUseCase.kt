package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.PaymentConfirmationMode
import xyz.lilsus.blip.domain.repository.PaymentPreferencesRepository

private const val MSATS_PER_SAT = 1_000L

class ShouldConfirmPaymentUseCase(private val repository: PaymentPreferencesRepository) {
    suspend operator fun invoke(
        amountMsats: Long,
        isManualEntry: Boolean,
        isShortcut: Boolean = false
    ): Boolean {
        require(amountMsats >= 0) { "amountMsats must be non-negative" }
        val preferences = repository.getPreferences()
        if (isShortcut && preferences.confirmShortcutPayments) {
            return true
        }
        if (isManualEntry && !preferences.confirmManualEntry) {
            return false
        }
        val amountSats = amountMsats / MSATS_PER_SAT
        return when (preferences.confirmationMode) {
            PaymentConfirmationMode.Always -> true
            PaymentConfirmationMode.Above -> amountSats >= preferences.thresholdSats
        }
    }
}
