@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.presentation.addconnection

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletDiscovery
import xyz.lilsus.papp.domain.model.WalletMetadataSnapshot
import xyz.lilsus.papp.domain.model.toMetadataSnapshot
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.usecases.DiscoverWalletUseCase
import xyz.lilsus.papp.domain.usecases.SetWalletConnectionUseCase

class ConnectWalletViewModelTest {
    private val walletRepository = FakeWalletSettingsRepository()
    private val discoveryRepository = FakeWalletDiscoveryRepository()
    private val discoverWallet = DiscoverWalletUseCase(discoveryRepository)
    private val setWalletConnection = SetWalletConnectionUseCase(walletRepository)

    @AfterTest
    fun tearDown() {
        walletRepository.reset()
        discoveryRepository.reset()
    }

    @Test
    fun loadEmitsDiscoveryAndAliasSuggestion() {
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            discoveryRepository.stub(VALID_URI, TEST_DISCOVERY)
            val viewModel = createViewModel(dispatcher)
            try {
                viewModel.load(VALID_URI)
                advanceUntilIdle()

                val state = viewModel.uiState.value
                assertEquals(VALID_URI, state.uri)
                assertEquals(TEST_DISCOVERY, state.discovery)
                assertEquals(TEST_DISCOVERY.aliasSuggestion, state.aliasInput)
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

                val eventDeferred = async {
                    viewModel.events.first { it is ConnectWalletEvent.Success }
                }

                viewModel.confirm()
                advanceUntilIdle()

                val event = eventDeferred.await() as ConnectWalletEvent.Success
                assertEquals("My Wallet", walletRepository.lastSavedAlias)
                assertEquals(event.connection.alias, walletRepository.lastSavedAlias)
                assertEquals(
                    TEST_DISCOVERY.toMetadataSnapshot(),
                    walletRepository.lastSavedMetadata
                )
                assertNotNull(walletRepository.getWalletConnection())
            } finally {
                viewModel.clear()
            }
        }
    }

    @Test
    fun updateAliasKeepsAliasSingleLine() {
        val viewModel = createViewModel()
        try {
            viewModel.updateAlias("My\nWallet\r\nAlias")

            assertEquals("My Wallet Alias", viewModel.uiState.value.aliasInput)
        } finally {
            viewModel.clear()
        }
    }

    private fun createViewModel(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined): ConnectWalletViewModel = ConnectWalletViewModel(
        discoverWallet = discoverWallet,
        setWalletConnection = setWalletConnection,
        dispatcher = dispatcher
    )

    private class FakeWalletDiscoveryRepository : WalletDiscoveryRepository {
        private val stubs = mutableMapOf<String, WalletDiscovery>()

        override suspend fun discover(uri: String): WalletDiscovery = stubs[uri]
            ?: throw AppErrorException(AppError.Unexpected("Missing stub for $uri"))

        fun stub(uri: String, discovery: WalletDiscovery) {
            stubs[uri] = discovery
        }

        fun reset() {
            stubs.clear()
        }
    }

    private class FakeWalletSettingsRepository : WalletSettingsRepository {
        private val walletFlow = MutableStateFlow<WalletConnection?>(null)
        var lastSavedAlias: String? = null
        var lastSavedMetadata: WalletMetadataSnapshot? = null

        override val walletConnection = walletFlow

        override suspend fun getWalletConnection(): WalletConnection? = walletFlow.value

        override suspend fun saveWalletConnection(connection: WalletConnection) {
            walletFlow.value = connection
            lastSavedAlias = connection.alias
            lastSavedMetadata = connection.metadata
        }

        override suspend fun clearWalletConnection() {
            walletFlow.value = null
        }

        fun reset() {
            walletFlow.value = null
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
            color = null
        )
    }
}
