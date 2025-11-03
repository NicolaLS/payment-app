package xyz.lilsus.papp.presentation.add_connection

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletDiscovery
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.use_cases.DiscoverWalletUseCase
import xyz.lilsus.papp.domain.use_cases.GetWalletsUseCase
import xyz.lilsus.papp.domain.use_cases.SetWalletConnectionUseCase
import xyz.lilsus.papp.domain.model.WalletMetadataSnapshot
import xyz.lilsus.papp.domain.model.toMetadataSnapshot

class ConnectWalletViewModelTest {
    private val walletRepository = FakeWalletSettingsRepository()
    private val discoveryRepository = FakeWalletDiscoveryRepository()
    private val discoverWallet = DiscoverWalletUseCase(discoveryRepository)
    private val setWalletConnection = SetWalletConnectionUseCase(walletRepository)
    private val getWallets = GetWalletsUseCase(walletRepository)

    @AfterTest
    fun tearDown() {
        walletRepository.reset()
        discoveryRepository.reset()
    }

    @Test
    fun loadEmitsDiscoveryAndAliasSuggestion() {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            walletRepository.saveWalletConnection(EXISTING_WALLET, activate = true)
            discoveryRepository.stub(VALID_URI, TEST_DISCOVERY)
            val viewModel = createViewModel(dispatcher)
            try {
                viewModel.load(VALID_URI)
                advanceUntilIdle()

                val state = viewModel.uiState.value
                assertEquals(VALID_URI, state.uri)
                assertEquals(TEST_DISCOVERY, state.discovery)
                assertEquals(TEST_DISCOVERY.aliasSuggestion, state.aliasInput)
                assertTrue(state.setActive)
            } finally {
                viewModel.clear()
            }
        }
    }

    @Test
    fun confirmSavesWalletAndEmitsSuccess() {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            discoveryRepository.stub(VALID_URI, TEST_DISCOVERY)
            val viewModel = createViewModel(dispatcher)
            try {
                viewModel.load(VALID_URI)
                advanceUntilIdle()

                assertNotNull(viewModel.uiState.value.discovery)
                viewModel.updateAlias(" My Wallet \n")
                viewModel.updateSetActive(true)

                val eventDeferred = async {
                    viewModel.events.first { it is ConnectWalletEvent.Success }
                }

                viewModel.confirm()
                advanceUntilIdle()

                val event = eventDeferred.await() as ConnectWalletEvent.Success
                assertEquals("My Wallet", walletRepository.lastSavedAlias)
                assertEquals(event.connection.alias, walletRepository.lastSavedAlias)
                assertEquals(TEST_DISCOVERY.toMetadataSnapshot(), walletRepository.lastSavedMetadata)
                assertNotNull(walletRepository.getWalletConnection())
            } finally {
                viewModel.clear()
            }
        }
    }

    private fun createViewModel(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined): ConnectWalletViewModel {
        return ConnectWalletViewModel(
            discoverWallet = discoverWallet,
            setWalletConnection = setWalletConnection,
            getWallets = getWallets,
            dispatcher = dispatcher,
        )
    }

    private class FakeWalletDiscoveryRepository : WalletDiscoveryRepository {
        private val stubs = mutableMapOf<String, WalletDiscovery>()

        override suspend fun discover(uri: String): WalletDiscovery {
            return stubs[uri] ?: throw AppErrorException(AppError.Unexpected("Missing stub for $uri"))
        }

        fun stub(uri: String, discovery: WalletDiscovery) {
            stubs[uri] = discovery
        }

        fun reset() {
            stubs.clear()
        }
    }

    private class FakeWalletSettingsRepository : WalletSettingsRepository {
        private var active: WalletConnection? = null
        private val storedWallets = mutableListOf<WalletConnection>()
        private val walletsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<WalletConnection>>(emptyList())
        private val activeFlow = kotlinx.coroutines.flow.MutableStateFlow<WalletConnection?>(null)
        var lastSavedAlias: String? = null
        var lastSavedMetadata: WalletMetadataSnapshot? = null

        override val wallets: Flow<List<WalletConnection>> = walletsFlow
        override val walletConnection: Flow<WalletConnection?> = activeFlow

        override suspend fun getWalletConnection(): WalletConnection? = active

        override suspend fun saveWalletConnection(connection: WalletConnection, activate: Boolean) {
            storedWallets.removeAll { it.walletPublicKey == connection.walletPublicKey }
            storedWallets.add(connection)
            if (activate) {
                active = connection
            }
            walletsFlow.value = storedWallets.toList()
            activeFlow.value = active
            lastSavedAlias = connection.alias
            lastSavedMetadata = connection.metadata
        }

        override suspend fun setActiveWallet(walletPublicKey: String) {
            active = storedWallets.firstOrNull { it.walletPublicKey == walletPublicKey }
            activeFlow.value = active
        }

        override suspend fun removeWallet(walletPublicKey: String) {
            storedWallets.removeAll { it.walletPublicKey == walletPublicKey }
            if (active?.walletPublicKey == walletPublicKey) {
                active = storedWallets.firstOrNull()
            }
            walletsFlow.value = storedWallets.toList()
            activeFlow.value = active
        }

        override suspend fun getWallets(): List<WalletConnection> = storedWallets.toList()

        override suspend fun clearWalletConnection() {
            storedWallets.clear()
            active = null
            walletsFlow.value = emptyList()
            activeFlow.value = null
        }

        fun reset() {
            storedWallets.clear()
            active = null
            walletsFlow.value = emptyList()
            activeFlow.value = null
            lastSavedAlias = null
            lastSavedMetadata = null
        }
    }

    companion object {
        private const val VALID_URI =
            "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4" +
                "?relay=wss://relay.example.com" +
                "&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100e4f3c"
        private val TEST_DISCOVERY = WalletDiscovery(
            uri = VALID_URI,
            walletPublicKey = "b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4",
            relayUrl = "wss://relay.example.com",
            lud16 = "user@example.com",
            aliasSuggestion = "Suggested",
            methods = setOf("pay_invoice", "get_balance"),
            encryptionSchemes = setOf("nip44_v2"),
            negotiatedEncryption = "nip44_v2",
            encryptionDefaultedToNip04 = false,
            notifications = emptySet(),
            network = "mainnet",
            color = null,
        )
        private val EXISTING_WALLET = WalletConnection(
            uri =
                "nostr+walletconnect://c889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d0" +
                    "?relay=wss://relay.example.com" +
                    "&secret=f1a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100e4f3d",
            walletPublicKey = "c889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d0",
            relayUrl = "wss://relay.example.com",
            lud16 = null,
            alias = "Existing",
        )
    }
}
