package xyz.lilsus.papp.presentation.add_connection

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.use_cases.ObserveWalletConnectionUseCase
import xyz.lilsus.papp.domain.use_cases.SetWalletConnectionUseCase

class ConnectWalletViewModelTest {
    private val repository = FakeWalletSettingsRepository()
    private val observeWalletConnection = ObserveWalletConnectionUseCase(repository)
    private val setWalletConnection = SetWalletConnectionUseCase(repository)

    @AfterTest
    fun tearDown() {
        repository.reset()
    }

    @Test
    fun prefillUriIfValid_populatesWhenFieldEmpty() {
        val viewModel = createViewModel()
        try {
            viewModel.prefillUriIfValid("  $VALID_NWC_URI  ")

            val state = viewModel.uiState.value
            assertEquals(VALID_NWC_URI, state.uri)
            assertTrue(state.error == null)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun prefillUriIfValid_ignoresInvalidClipboardValue() {
        val viewModel = createViewModel()
        try {
            viewModel.prefillUriIfValid("not a nwc link")

            val state = viewModel.uiState.value
            assertTrue(state.uri.isBlank())
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun prefillUriIfValid_doesNotOverrideExistingValue() {
        val viewModel = createViewModel()
        try {
            viewModel.updateUri("existing")

            viewModel.prefillUriIfValid(VALID_NWC_URI)

            val state = viewModel.uiState.value
            assertEquals("existing", state.uri)
        } finally {
            viewModel.clear()
        }
    }

    private fun createViewModel(): ConnectWalletViewModel {
        return ConnectWalletViewModel(
            setWalletConnection = setWalletConnection,
            observeWalletConnection = observeWalletConnection,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    private class FakeWalletSettingsRepository : WalletSettingsRepository {
        private val active = MutableStateFlow<WalletConnection?>(null)
        private val stored = MutableStateFlow<List<WalletConnection>>(emptyList())

        override val wallets: Flow<List<WalletConnection>> = stored
        override val walletConnection: Flow<WalletConnection?> = active

        override suspend fun getWalletConnection(): WalletConnection? = active.value

        override suspend fun saveWalletConnection(connection: WalletConnection) {
            stored.value = stored.value + connection
            active.value = connection
        }

        override suspend fun setActiveWallet(walletPublicKey: String) {
            active.value = stored.value.firstOrNull { it.walletPublicKey == walletPublicKey }
        }

        override suspend fun removeWallet(walletPublicKey: String) {
            stored.value = stored.value.filterNot { it.walletPublicKey == walletPublicKey }
            if (active.value?.walletPublicKey == walletPublicKey) {
                active.value = null
            }
        }

        override suspend fun getWallets(): List<WalletConnection> = stored.value

        override suspend fun clearWalletConnection() {
            active.value = null
        }

        fun reset() {
            active.value = null
            stored.value = emptyList()
        }
    }

    companion object {
        private const val VALID_NWC_URI =
            "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4" +
                "?relay=wss%3A%2F%2Frelay.damus.io" +
                "&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100e4f3c"
    }
}
