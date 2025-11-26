package xyz.lilsus.papp.domain.use_cases

import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class GetWalletsUseCase(private val repository: WalletSettingsRepository) {
    suspend operator fun invoke() = repository.getWallets()
}
