package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.model.WalletDiscovery
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository

class DiscoverWalletUseCase(private val repository: WalletDiscoveryRepository) {
    suspend operator fun invoke(uri: String): WalletDiscovery = repository.discover(uri)
}
