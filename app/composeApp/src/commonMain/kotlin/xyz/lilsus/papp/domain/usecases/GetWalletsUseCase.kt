package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class GetWalletsUseCase(private val repository: WalletSettingsRepository) {
    suspend operator fun invoke() = repository.getWallets()
}
