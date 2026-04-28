package xyz.lilsus.papp.domain.repository

import xyz.lilsus.papp.domain.model.WalletDiscovery

interface WalletDiscoveryRepository {
    suspend fun discover(uri: String): WalletDiscovery
}
