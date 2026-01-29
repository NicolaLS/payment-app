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
 * Clients are cached by wallet URI and reused across operations. The active wallet's
 * client is proactively created on startup for faster first payment. Clients are
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
    private val clients = mutableMapOf<String, NwcClient>()
    private val mutex = Mutex()

    init {
        scope.launch {
            combine(
                appLifecycle.isInForeground,
                walletSettings.walletConnection
            ) { isForeground, activeConnection ->
                isForeground to activeConnection
            }.collectLatest { (isForeground, activeConnection) ->
                if (isForeground && activeConnection != null) {
                    // Proactively create client for the active wallet (auto-connects on creation)
                    // Best-effort: if this fails, client will be created on-demand
                    runCatching { getOrCreateClient(activeConnection) }
                } else if (!isForeground) {
                    disconnectAll()
                }
            }
        }
    }

    /**
     * Returns a cached or new NWC client for the given connection.
     *
     * If a specific connection is provided, returns a client for that connection.
     * Otherwise, returns a client for the currently active wallet.
     *
     * The client auto-connects on creation and handles reconnection internally,
     * so the returned client is ready to use (operations will wait for connection).
     */
    suspend fun getClient(specificConnection: WalletConnection? = null): NwcClient {
        val connection = specificConnection ?: walletSettings.getWalletConnection()
            ?: throw AppErrorException(AppError.MissingWalletConnection)
        return getOrCreateClient(connection)
    }

    /**
     * Gets an existing client from cache or creates a new one.
     * Client creation is synchronous, so we can safely do everything inside the mutex.
     */
    private suspend fun getOrCreateClient(connection: WalletConnection): NwcClient =
        mutex.withLock {
            clients.getOrPut(connection.uri) {
                clientFactory.create(connection)
            }
        }

    /**
     * Closes all cached clients and clears the cache.
     * Called when the app goes to background.
     */
    suspend fun disconnectAll() {
        val clientsToClose = mutex.withLock {
            val copy = clients.values.toList()
            clients.clear()
            copy
        }
        // Close clients outside the mutex. Errors are ignored since close()
        // is best-effort cleanup and failures are non-actionable.
        clientsToClose.forEach { client ->
            runCatching { client.close() }
        }
    }
}
