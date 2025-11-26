package xyz.lilsus.papp.data.nwc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.toMetadataSnapshot
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class WalletMetadataSynchronizer(
    private val scope: CoroutineScope,
    private val discoveryRepository: WalletDiscoveryRepository,
    private val walletSettingsRepository: WalletSettingsRepository
) {
    fun start() {
        scope.launch {
            // Only refresh metadata for the active wallet to avoid unnecessary network calls
            // Inactive wallets' metadata will be refreshed if/when they become active
            val active = runCatching { walletSettingsRepository.getWalletConnection() }
                .getOrNull() ?: return@launch

            // Skip if metadata already exists and is fresh
            // (In the future, could add timestamp check here to refresh stale metadata)
            if (active.metadata != null) return@launch

            val snapshot = runCatching { discoveryRepository.discover(active.uri) }
                .map { it.toMetadataSnapshot() }
                .getOrElse { return@launch }

            walletSettingsRepository.saveWalletConnection(
                connection = active.copy(metadata = snapshot),
                activate = true
            )
        }
    }
}
