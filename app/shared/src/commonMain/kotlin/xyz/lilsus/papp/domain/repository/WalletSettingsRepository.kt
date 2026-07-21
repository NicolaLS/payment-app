package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow
import xyz.lilsus.papp.domain.model.WalletConnection

/** Persists the app's single optional wallet connection. */
interface WalletSettingsRepository {
    /**
     * Emits the connected wallet, or `null` if none is configured.
     */
    val walletConnection: Flow<WalletConnection?>

    suspend fun getWalletConnection(): WalletConnection?

    /** Saves [connection]. Fails if another wallet is already connected. */
    suspend fun saveWalletConnection(connection: WalletConnection)

    suspend fun clearWalletConnection()
}
