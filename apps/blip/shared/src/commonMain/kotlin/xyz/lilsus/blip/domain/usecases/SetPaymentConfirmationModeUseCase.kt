package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.PaymentConfirmationMode
import xyz.lilsus.blip.domain.repository.PaymentPreferencesRepository

class SetPaymentConfirmationModeUseCase(private val repository: PaymentPreferencesRepository) {
    suspend operator fun invoke(mode: PaymentConfirmationMode) {
        repository.setConfirmationMode(mode)
    }
}
