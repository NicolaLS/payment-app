package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class RemoveWalletConnectionUseCase(private val repository: WalletSettingsRepository) {
    suspend operator fun invoke(walletPublicKey: String) = repository.removeWallet(walletPublicKey)
}
