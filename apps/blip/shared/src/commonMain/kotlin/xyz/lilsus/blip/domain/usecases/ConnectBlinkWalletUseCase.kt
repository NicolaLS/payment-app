package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.WalletConnection
import xyz.lilsus.blip.domain.repository.BlinkWalletAccountRepository

class ConnectBlinkWalletUseCase(private val repository: BlinkWalletAccountRepository) {
    suspend operator fun invoke(apiKey: String, alias: String): WalletConnection =
        repository.connect(apiKey = apiKey, alias = alias)
}
