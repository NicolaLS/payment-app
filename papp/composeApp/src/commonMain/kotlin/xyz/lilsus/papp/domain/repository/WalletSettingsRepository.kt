package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.WalletConnection

/**
 * Abstraction for persisting the active Nostr Wallet Connect credential.
 */
interface WalletSettingsRepository {
    /**
     * Emits the full list of stored wallet connections.
     */
    val wallets: Flow<List<WalletConnection>>

    /**
     * Emits the currently active wallet connection, or `null` if none is configured.
     */
    val walletConnection: Flow<WalletConnection?>

    suspend fun getWalletConnection(): WalletConnection?

    suspend fun saveWalletConnection(connection: WalletConnection)

    suspend fun setActiveWallet(walletPublicKey: String)

    suspend fun removeWallet(walletPublicKey: String)

    suspend fun getWallets(): List<WalletConnection>

    suspend fun clearWalletConnection()
}
