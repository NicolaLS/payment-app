package xyz.lilsus.papp.data.nwc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.toMetadataSnapshot
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

class WalletMetadataSynchronizer(
    private val scope: CoroutineScope,
    private val discoveryRepository: WalletDiscoveryRepository,
    private val walletSettingsRepository: WalletSettingsRepository,
) {
    fun start() {
        scope.launch {
            val wallets = runCatching { walletSettingsRepository.getWallets() }
                .getOrElse { emptyList() }
            if (wallets.isEmpty()) return@launch

            val active = runCatching { walletSettingsRepository.getWalletConnection() }.getOrNull()
            wallets.forEach { wallet ->
                launch {
                    val snapshot = runCatching { discoveryRepository.discover(wallet.uri) }
                        .map { it.toMetadataSnapshot() }
                        .getOrElse { return@launch }
                    walletSettingsRepository.saveWalletConnection(
                        connection = wallet.copy(metadata = snapshot),
                        activate = wallet.walletPublicKey == active?.walletPublicKey,
                    )
                }
            }
        }
    }
}
