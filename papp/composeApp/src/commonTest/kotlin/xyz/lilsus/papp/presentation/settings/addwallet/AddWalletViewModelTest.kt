@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.presentation.settings.addwallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

class AddWalletViewModelTest {

    @Test
    fun submitEmitsNavigationEventForValidUri() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(dispatcher)
        try {
            viewModel.updateUri(VALID_URI)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canContinue)

            val eventDeferred = async { viewModel.events.first() }
            viewModel.submit()
            advanceUntilIdle()

            val event = eventDeferred.await()
            assertTrue(event is AddWalletEvent.NavigateToConfirm)
            assertEquals(VALID_URI, event.uri)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun submitSetsErrorWhenUriBlank() {
        val viewModel = createViewModel()
        try {
            viewModel.submit()

            assertNotNull(viewModel.uiState.value.error)
            assertTrue(!viewModel.uiState.value.canContinue)
        } finally {
            viewModel.clear()
        }
    }

    @Test
    fun handleScannedValueTrimsAndAllowsSubmit() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = createViewModel(dispatcher)
        try {
            val eventDeferred = async { viewModel.events.first() }

            viewModel.handleScannedValue("  $VALID_URI  ")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(VALID_URI, state.uri)
            assertTrue(state.canContinue)

            val event = eventDeferred.await()
            assertTrue(event is AddWalletEvent.NavigateToConfirm)
            assertEquals(VALID_URI, event.uri)
        } finally {
            viewModel.clear()
        }
    }

    private fun createViewModel(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined): AddWalletViewModel =
        AddWalletViewModel(dispatcher = dispatcher)

    companion object {
        private const val VALID_URI =
            "nostr+walletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4" +
                "?relay=wss://relay.example.com" +
                "&secret=71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100e4f3c"
    }
}
