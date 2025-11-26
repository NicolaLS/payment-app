package xyz.lilsus.papp.domain.use_cases

import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.repository.PaymentPreferencesRepository

class SetPaymentConfirmationModeUseCase(private val repository: PaymentPreferencesRepository) {
    suspend operator fun invoke(mode: PaymentConfirmationMode) {
        repository.setConfirmationMode(mode)
    }
}
