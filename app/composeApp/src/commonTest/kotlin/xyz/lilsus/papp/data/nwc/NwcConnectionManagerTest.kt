@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.data.nwc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.platform.AppLifecycleObserver

class NwcConnectionManagerTest {

    @Test
    fun foregroundBlinkWalletDoesNotPrewarmNwcClient() = runTest {
        var createCalls = 0
        NwcConnectionManager(
            appLifecycle = FakeAppLifecycleObserver(isInForeground = true),
            walletSettings = FakeWalletSettingsRepository(activeWallet = blinkWallet()),
            clientFactory = failingFactory { createCalls += 1 },
            scope = backgroundScope
        )

        runCurrent()

        assertEquals(0, createCalls)
    }

    @Test
    fun getClientRejectsBlinkConnectionBeforeCreatingClient() = runTest {
        var createCalls = 0
        val manager = NwcConnectionManager(
            appLifecycle = FakeAppLifecycleObserver(isInForeground = true),
            walletSettings = FakeWalletSettingsRepository(activeWallet = null),
            clientFactory = failingFactory { createCalls += 1 },
            scope = backgroundScope
        )

        val exception = assertFailsWith<AppErrorException> {
            manager.getClient(blinkWallet())
        }

        assertEquals(AppError.MissingWalletConnection, exception.error)
        assertEquals(0, createCalls)
    }

    @Test
    fun getClientRejectsBlankNwcUriBeforeCreatingClient() = runTest {
        var createCalls = 0
        val manager = NwcConnectionManager(
            appLifecycle = FakeAppLifecycleObserver(isInForeground = true),
            walletSettings = FakeWalletSettingsRepository(activeWallet = null),
            clientFactory = failingFactory { createCalls += 1 },
            scope = backgroundScope
        )

        val exception = assertFailsWith<AppErrorException> {
            manager.getClient(nwcWallet(uri = ""))
        }

        assertEquals(AppError.InvalidWalletUri("NWC wallet URI is empty"), exception.error)
        assertEquals(0, createCalls)
    }

    private fun failingFactory(onCreate: (WalletConnection) -> Unit) = NwcClientFactory { connection ->
        onCreate(connection)
        error("NWC client factory should not be called")
    }

    private fun blinkWallet(): WalletConnection = WalletConnection(
        walletPublicKey = "blink-wallet",
        alias = "Blink",
        type = WalletType.BLINK
    )

    private fun nwcWallet(uri: String): WalletConnection = WalletConnection(
        walletPublicKey = "nwc-wallet",
        alias = "NWC",
        type = WalletType.NWC,
        uri = uri
    )

    private class FakeAppLifecycleObserver(isInForeground: Boolean) : AppLifecycleObserver {
        override val isInForeground = MutableStateFlow(isInForeground)
    }

    private class FakeWalletSettingsRepository(activeWallet: WalletConnection?) : WalletSettingsRepository {

        private val activeWalletState = MutableStateFlow(activeWallet)
        private val walletsState = MutableStateFlow(activeWallet?.let(::listOf) ?: emptyList())

        override val wallets: Flow<List<WalletConnection>> = walletsState
        override val walletConnection: Flow<WalletConnection?> = activeWalletState

        override suspend fun getWalletConnection(): WalletConnection? = activeWalletState.value

        override suspend fun saveWalletConnection(connection: WalletConnection, activate: Boolean) {
            walletsState.value = walletsState.value.filterNot {
                it.walletPublicKey == connection.walletPublicKey
            } + connection
            if (activate) {
                activeWalletState.value = connection
            }
        }

        override suspend fun setActiveWallet(walletPublicKey: String) {
            activeWalletState.value = walletsState.value.firstOrNull {
                it.walletPublicKey == walletPublicKey
            }
        }

        override suspend fun removeWallet(walletPublicKey: String) {
            walletsState.value = walletsState.value.filterNot {
                it.walletPublicKey == walletPublicKey
            }
            if (activeWalletState.value?.walletPublicKey == walletPublicKey) {
                activeWalletState.value = walletsState.value.firstOrNull()
            }
        }

        override suspend fun getWallets(): List<WalletConnection> = walletsState.value

        override suspend fun clearWalletConnection() {
            walletsState.value = emptyList()
            activeWalletState.value = null
        }
    }
}
