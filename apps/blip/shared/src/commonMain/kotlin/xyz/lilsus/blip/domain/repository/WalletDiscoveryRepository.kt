package xyz.lilsus.blip.domain.repository

import xyz.lilsus.blip.domain.model.WalletDiscovery

interface WalletDiscoveryRepository {
    suspend fun discover(uri: String): WalletDiscovery
}
