package xyz.lilsus.papp.domain.use_cases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.domain.repository.PaymentPreferencesRepository

class ObservePaymentPreferencesUseCase(private val repository: PaymentPreferencesRepository) {
    operator fun invoke(): Flow<PaymentPreferences> = repository.preferences
}
