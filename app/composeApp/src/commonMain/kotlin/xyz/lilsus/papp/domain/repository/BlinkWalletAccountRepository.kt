package xyz.lilsus.papp.domain.repository

import xyz.lilsus.papp.domain.model.WalletConnection

/**
 * Manages Blink wallet account credentials and wallet-specific settings.
 */
interface BlinkWalletAccountRepository {
    suspend fun connect(apiKey: String, alias: String): WalletConnection

    suspend fun getCachedDefaultWalletId(walletId: String): String?

    suspend fun refreshDefaultWalletId(walletId: String): String
}
