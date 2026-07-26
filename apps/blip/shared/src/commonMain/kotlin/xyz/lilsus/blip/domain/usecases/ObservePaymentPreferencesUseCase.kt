package xyz.lilsus.blip.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.blip.domain.model.PaymentPreferences
import xyz.lilsus.blip.domain.repository.PaymentPreferencesRepository

class ObservePaymentPreferencesUseCase(private val repository: PaymentPreferencesRepository) {
    operator fun invoke(): Flow<PaymentPreferences> = repository.preferences
}
