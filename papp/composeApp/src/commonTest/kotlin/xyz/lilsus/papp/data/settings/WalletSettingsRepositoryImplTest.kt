@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType

class WalletSettingsRepositoryImplTest {

    @Test
    fun removeWalletCallsRemovalHookWithRemovedWallet() = runTest {
        val removed = mutableListOf<WalletConnection>()
        val repository = createRepository { removed.add(it) }
        val blinkWallet = walletConnection("blink-wallet", WalletType.BLINK)

        repository.saveWalletConnection(blinkWallet, activate = true)
        repository.removeWallet(blinkWallet.walletPublicKey)

        assertEquals(listOf(blinkWallet), removed)
    }

    @Test
    fun clearWalletConnectionCallsRemovalHookForAllWallets() = runTest {
        val removed = mutableListOf<WalletConnection>()
        val repository = createRepository { removed.add(it) }
        val nwcWallet = walletConnection("nwc-wallet", WalletType.NWC)
        val blinkWallet = walletConnection("blink-wallet", WalletType.BLINK)

        repository.saveWalletConnection(nwcWallet, activate = false)
        repository.saveWalletConnection(blinkWallet, activate = true)
        repository.clearWalletConnection()

        assertEquals(setOf(nwcWallet, blinkWallet), removed.toSet())
    }

    private fun TestScope.createRepository(onRemoved: (WalletConnection) -> Unit): WalletSettingsRepositoryImpl {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repository = WalletSettingsRepositoryImpl(
            settings = MapSettings(),
            dispatcher = dispatcher,
            scope = this,
            onWalletRemoved = onRemoved
        )
        runCurrent()
        return repository
    }

    private fun walletConnection(id: String, type: WalletType): WalletConnection = WalletConnection(
        walletPublicKey = id,
        alias = "wallet-$id",
        type = type,
        uri = if (type == WalletType.NWC) {
            "nostr+walletconnect://$id?relay=wss://relay.example&secret=sec"
        } else {
            ""
        }
    )
}
