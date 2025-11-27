package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class ClearWalletConnectionUseCase(private val repository: WalletSettingsRepository) {
    suspend operator fun invoke(walletPublicKey: String? = null) {
        if (walletPublicKey != null) {
            repository.removeWallet(walletPublicKey)
        } else {
            val active = repository.getWalletConnection()
            if (active != null) {
                repository.removeWallet(active.walletPublicKey)
            } else {
                repository.clearWalletConnection()
            }
        }
    }
}
