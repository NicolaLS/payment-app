package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.WalletConnection

/**
 * Abstraction for persisting the active Nostr Wallet Connect credential.
 */
interface WalletSettingsRepository {
    /**
     * Emits the currently active wallet connection, or `null` if none is configured.
     */
    val walletConnection: Flow<WalletConnection?>

    suspend fun getWalletConnection(): WalletConnection?

    suspend fun saveWalletConnection(connection: WalletConnection)

    suspend fun clearWalletConnection()
}
