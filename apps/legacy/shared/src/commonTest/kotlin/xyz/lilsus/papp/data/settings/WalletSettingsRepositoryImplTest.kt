@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType

class WalletSettingsRepositoryImplTest {

    @Test
    fun savesAndLoadsTheConnectedWallet() = runTest {
        val settings = MapSettings()
        val wallet = walletConnection("nwc-wallet", WalletType.NWC)
        WalletSettingsRepositoryImpl(settings = settings).saveWalletConnection(wallet)

        val restored = WalletSettingsRepositoryImpl(settings = settings).getWalletConnection()

        assertEquals(wallet, restored)
    }

    @Test
    fun rejectsConnectingASecondWallet() = runTest {
        val repository = WalletSettingsRepositoryImpl(settings = MapSettings())
        val first = walletConnection("nwc-wallet", WalletType.NWC)
        repository.saveWalletConnection(first)

        val exception = assertFailsWith<AppErrorException> {
            repository.saveWalletConnection(walletConnection("blink-wallet", WalletType.BLINK))
        }

        assertEquals(AppError.WalletAlreadyConnected, exception.error)
        assertEquals(first, repository.getWalletConnection())
    }

    @Test
    fun clearRemovesTheWalletAndCallsTheRemovalHook() = runTest {
        val removed = mutableListOf<WalletConnection>()
        val settings = MapSettings()
        val repository = WalletSettingsRepositoryImpl(
            settings = settings,
            onWalletRemoved = { removed.add(it) }
        )
        val wallet = walletConnection("blink-wallet", WalletType.BLINK)
        repository.saveWalletConnection(wallet)

        repository.clearWalletConnection()

        assertNull(repository.getWalletConnection())
        assertNull(settings.getStringOrNull("wallet.connection"))
        assertEquals(listOf(wallet), removed)
    }

    @Test
    fun migratesThePreviouslyActiveLegacyWalletAndDiscardsTheOthers() = runTest {
        val settings = MapSettings(
            "wallet.list" to """[
                {"walletPublicKey":"first","alias":"First","type":"NWC"},
                {"walletPublicKey":"second","alias":"Second","type":"BLINK"}
            ]
            """.trimIndent(),
            "wallet.active" to "second"
        )
        var retained: WalletConnection? = null
        var discarded: List<WalletConnection> = emptyList()

        val repository = WalletSettingsRepositoryImpl(
            settings = settings,
            onLegacyWalletsMigrated = { migratedRetained, migratedDiscarded ->
                retained = migratedRetained
                discarded = migratedDiscarded
            }
        )

        assertEquals("second", repository.getWalletConnection()?.walletPublicKey)
        assertEquals("second", retained?.walletPublicKey)
        assertEquals(listOf("first"), discarded.map { it.walletPublicKey })
        assertNull(settings.getStringOrNull("wallet.list"))
        assertNull(settings.getStringOrNull("wallet.active"))
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
