package xyz.lilsus.blip.domain.usecases

import xyz.lilsus.blip.domain.model.WalletDiscovery
import xyz.lilsus.blip.domain.repository.WalletDiscoveryRepository

class DiscoverWalletUseCase(private val repository: WalletDiscoveryRepository) {
    suspend operator fun invoke(uri: String): WalletDiscovery = repository.discover(uri)
}
