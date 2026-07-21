package xyz.lilsus.papp.data.nwc

import io.github.nicolals.nwc.NwcClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.platform.AppLifecycleObserver

/**
 * Manages NWC client instances with caching and lifecycle awareness.
 *
 * The connected wallet's client is reused across operations and proactively created on startup.
 * It is
 * closed when the app goes to background to release resources.
 *
 * Note: NwcClient auto-connects when created and handles reconnection internally,
 * so no explicit connect() calls are needed.
 */
class NwcConnectionManager(
    private val appLifecycle: AppLifecycleObserver,
    private val walletSettings: WalletSettingsRepository,
    private val clientFactory: NwcClientFactory,
    scope: CoroutineScope
) {
    private var client: NwcClient? = null
    private val mutex = Mutex()

    init {
        scope.launch {
            combine(
                appLifecycle.isInForeground,
                walletSettings.walletConnection
            ) { isForeground, activeConnection ->
                isForeground to activeConnection
            }.collectLatest { (isForeground, activeConnection) ->
                if (isForeground && activeConnection?.isNwc == true) {
                    // Proactively create client for the active NWC wallet (auto-connects on creation)
                    // Best-effort: if this fails, client will be created on-demand
                    runCatching { getOrCreateClient(activeConnection) }
                } else if (!isForeground) {
                    disconnectAll()
                }
            }
        }
    }

    /**
     * Returns the cached client or creates one for the connected wallet.
     *
     * The client auto-connects on creation and handles reconnection internally,
     * so the returned client is ready to use (operations will wait for connection).
     */
    suspend fun getClient(): NwcClient {
        val connection = walletSettings.getWalletConnection()
            ?: throw AppErrorException(AppError.MissingWalletConnection)
        return getOrCreateClient(connection)
    }

    /**
     * Gets an existing client from cache or creates a new one.
     * Client creation is synchronous, so we can safely do everything inside the mutex.
     */
    private suspend fun getOrCreateClient(connection: WalletConnection): NwcClient {
        connection.requireNwcUri()
        return mutex.withLock {
            client ?: clientFactory.create(connection).also { client = it }
        }
    }

    /**
     * Closes all cached clients and clears the cache.
     * Called when the app goes to background.
     */
    suspend fun disconnectAll() {
        val clientToClose = mutex.withLock {
            val current = client
            client = null
            current
        }
        // Close clients outside the mutex. Errors are ignored since close()
        // is best-effort cleanup and failures are non-actionable.
        clientToClose?.let { runCatching { it.close() } }
    }

    /**
     * Evicts and closes the cached client.
     * Called when a wallet is removed so its credentials and websocket
     * are not retained in memory until the app backgrounds.
     */
    suspend fun evict() = disconnectAll()
}

private fun WalletConnection.requireNwcUri(): String {
    if (!isNwc) {
        throw AppErrorException(AppError.MissingWalletConnection)
    }
    return uri.takeIf { it.isNotBlank() }
        ?: throw AppErrorException(AppError.InvalidWalletUri("NWC wallet URI is empty"))
}
