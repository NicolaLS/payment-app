@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.presentation.settings.addblink

import dev.mokkery.answering.calls
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkErrorType
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository
import xyz.lilsus.papp.domain.usecases.ConnectBlinkWalletUseCase

class AddBlinkWalletViewModelTest {
    @Test
    fun submitWithEmptyAliasShowsError() {
        val repository = mock<BlinkWalletAccountRepository>()
        val context = createTestContext(repository)

        context.viewModel.updateAlias("   ")
        context.viewModel.updateApiKey("blink_key")
        context.viewModel.submit()

        val state = context.viewModel.uiState.value
        assertNotNull(state.error)
        assertTrue(state.error is AppError.InvalidWalletUri)

        context.viewModel.clear()
    }

    @Test
    fun submitWithEmptyApiKeyShowsError() {
        val repository = mock<BlinkWalletAccountRepository>()
        val context = createTestContext(repository)

        context.viewModel.updateAlias("My Wallet")
        context.viewModel.updateApiKey("   ")
        context.viewModel.submit()

        val state = context.viewModel.uiState.value
        assertNotNull(state.error)
        assertTrue(state.error is AppError.AuthenticationFailure)

        context.viewModel.clear()
    }

    @Test
    fun submitWithValidCredentialsEmitsSuccess() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = mock<BlinkWalletAccountRepository>()
        everySuspend { repository.connect(apiKey = any<String>(), alias = any<String>()) } calls { (apiKey: String, alias: String) ->
            WalletConnection(
                alias = alias,
                walletPublicKey = ""
            )
        }
        val context = createTestContext(repository = repository, dispatcher = dispatcher)

        context.viewModel.updateAlias(" My Wallet ")
        context.viewModel.updateApiKey(" full_key ")
        val eventDeferred = async { context.viewModel.events.first() }

        context.viewModel.submit()
        advanceUntilIdle()

        val event = eventDeferred.await() as AddBlinkWalletEvent.Success
        assertEquals("My Wallet", event.connection.alias)
        verifySuspend {
            repository.connect("full_key", "My Wallet")
        }
        context.viewModel.clear()
    }

    @Test
    fun submitWithReadOnlyApiKeyShowsPermissionDeniedError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = mock<BlinkWalletAccountRepository>()
        everySuspend {
            repository.connect(
                any<String>(),
                any<String>()
            )
        } throws AppErrorException(AppError.BlinkError(BlinkErrorType.PermissionDenied))
        val context = createTestContext(
            repository = repository,
            dispatcher = dispatcher
        )

        context.viewModel.updateAlias("My Wallet")
        context.viewModel.updateApiKey("read_only_key")
        context.viewModel.submit()
        advanceUntilIdle()

        val state = context.viewModel.uiState.value
        assertNotNull(state.error)
        val error = state.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.PermissionDenied, error.type)
        assertFalse(state.isSaving)

        context.viewModel.clear()
    }

    private fun createTestContext(
        repository: BlinkWalletAccountRepository,
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    ): TestContext {
        val viewModel = AddBlinkWalletViewModel(
            connectBlinkWallet = ConnectBlinkWalletUseCase(repository),
            dispatcher = dispatcher
        )
        return TestContext(viewModel)
    }

    private data class TestContext(val viewModel: AddBlinkWalletViewModel)
}
