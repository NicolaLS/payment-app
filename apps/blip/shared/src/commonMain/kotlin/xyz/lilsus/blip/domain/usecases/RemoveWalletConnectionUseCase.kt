package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.repository.WalletSettingsRepository

class RemoveWalletConnectionUseCase(private val repository: WalletSettingsRepository) {
    suspend operator fun invoke() = repository.clearWalletConnection()
}
