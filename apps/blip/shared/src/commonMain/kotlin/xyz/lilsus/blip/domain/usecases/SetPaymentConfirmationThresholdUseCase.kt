package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.PaymentPreferences
import xyz.lilsus.blip.domain.repository.PaymentPreferencesRepository

class SetPaymentConfirmationThresholdUseCase(private val repository: PaymentPreferencesRepository) {
    suspend operator fun invoke(thresholdSats: Long) {
        val normalised = thresholdSats.coerceIn(
            PaymentPreferences.MIN_CONFIRMATION_THRESHOLD_SATS,
            PaymentPreferences.MAX_CONFIRMATION_THRESHOLD_SATS
        )
        repository.setConfirmationThreshold(normalised)
    }
}
