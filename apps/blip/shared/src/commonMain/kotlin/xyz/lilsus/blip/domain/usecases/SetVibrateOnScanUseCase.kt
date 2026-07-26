package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.repository.PaymentPreferencesRepository

class SetVibrateOnScanUseCase(private val repository: PaymentPreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.setVibrateOnScan(enabled)
    }
}
