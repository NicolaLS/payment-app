package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.repository.PaymentPreferencesRepository

class SetConfirmShortcutPaymentsUseCase(private val repository: PaymentPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setConfirmShortcutPayments(enabled)
    }
}
