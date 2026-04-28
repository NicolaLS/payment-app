package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class ObserveWalletsUseCase(private val repository: WalletSettingsRepository) {
    operator fun invoke(): Flow<List<WalletConnection>> = repository.wallets
}
