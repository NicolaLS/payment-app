package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.WalletConnection

/**
 * Abstraction for persisting wallet connections and the active wallet selection.
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

    suspend fun saveWalletConnection(connection: WalletConnection, activate: Boolean = true)

    suspend fun setActiveWallet(walletPublicKey: String)

    suspend fun removeWallet(walletPublicKey: String)

    suspend fun getWallets(): List<WalletConnection>

    suspend fun clearWalletConnection()
}
