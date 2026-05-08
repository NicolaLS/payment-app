package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.PaymentPreferencesRepository

class SetConfirmShortcutPaymentsUseCase(private val repository: PaymentPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setConfirmShortcutPayments(enabled)
    }
}
