package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository

class ConnectBlinkWalletUseCase(private val repository: BlinkWalletAccountRepository) {
    suspend operator fun invoke(apiKey: String, alias: String): WalletConnection =
        repository.connect(apiKey = apiKey, alias = alias)
}
