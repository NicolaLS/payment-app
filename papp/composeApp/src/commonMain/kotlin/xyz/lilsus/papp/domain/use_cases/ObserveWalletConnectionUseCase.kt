package xyz.lilsus.papp.domain.use_cases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class ObserveWalletConnectionUseCase(private val repository: WalletSettingsRepository) {
    operator fun invoke(): Flow<WalletConnection?> = repository.walletConnection
}
