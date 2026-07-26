package xyz.lilsus.blip.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.blip.domain.model.WalletConnection
import xyz.lilsus.blip.domain.repository.WalletSettingsRepository

class ObserveWalletConnectionUseCase(private val repository: WalletSettingsRepository) {
    operator fun invoke(): Flow<WalletConnection?> = repository.walletConnection
}
