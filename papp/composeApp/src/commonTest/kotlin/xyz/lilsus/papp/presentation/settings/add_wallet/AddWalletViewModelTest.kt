package xyz.lilsus.papp.presentation.settings.add_wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AddWalletViewModelTest {

    @Test
    fun submitEmitsNavigationEventForValidUri() = runBlocking<Unit> {
        val viewModel = AddWalletViewModel(dispatcher = Dispatchers.Unconfined)
        viewModel.updateUri(VALID_URI)
        assertTrue(viewModel.uiState.value.canContinue)

        viewModel.submit()

        val event = viewModel.events.first()
        assertTrue(event is AddWalletEvent.NavigateToConfirm)
        assertEquals(VALID_URI, event.uri)
    }

    @Test
    fun submitSetsErrorWhenUriBlank() {
        val viewModel = AddWalletViewModel(dispatcher = Dispatchers.Unconfined)

        viewModel.submit()

        assertNotNull(viewModel.uiState.value.error)
        assertTrue(!viewModel.uiState.value.canContinue)
    }

    @Test
    fun handleScannedValueTrimsAndAllowsSubmit() = runBlocking<Unit> {
        val viewModel = AddWalletViewModel(dispatcher = Dispatchers.Unconfined)

        viewModel.handleScannedValue("  $VALID_URI  ")

        val state = viewModel.uiState.value
        assertEquals(VALID_URI, state.uri)
        assertTrue(state.canContinue)
        viewModel.submit()
        val event = viewModel.events.first()
        assertTrue(event is AddWalletEvent.NavigateToConfirm)
        assertEquals(VALID_URI, event.uri)
    }

    companion object {
        private const val VALID_URI = "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4" +
            "?relay=wss://relay.example.com" +
            "&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100e4f3c"
    }
}
