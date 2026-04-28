package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class SetActiveWalletUseCase(private val repository: WalletSettingsRepository) {
    suspend operator fun invoke(walletPublicKey: String) {
        repository.setActiveWallet(walletPublicKey)
    }
}
