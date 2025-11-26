package xyz.lilsus.papp.domain.use_cases

import xyz.lilsus.papp.domain.repository.PaymentPreferencesRepository

class SetVibrateOnPaymentUseCase(private val repository: PaymentPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setVibrateOnPayment(enabled)
    }
}
