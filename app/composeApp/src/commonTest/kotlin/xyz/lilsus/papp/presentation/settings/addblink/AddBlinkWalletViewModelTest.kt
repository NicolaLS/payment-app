@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.presentation.settings.addblink

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
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.BlinkWalletAccountRepository
import xyz.lilsus.papp.domain.usecases.ConnectBlinkWalletUseCase

class AddBlinkWalletViewModelTest {
    @Test
    fun submitWithEmptyAliasShowsError() {
        val context = createTestContext()

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
        val context = createTestContext()

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
        val repository = FakeBlinkWalletAccountRepository()
        val context = createTestContext(repository = repository, dispatcher = dispatcher)

        context.viewModel.updateAlias(" My Wallet ")
        context.viewModel.updateApiKey(" full_key ")
        val eventDeferred = async { context.viewModel.events.first() }

        context.viewModel.submit()
        advanceUntilIdle()

        val event = eventDeferred.await() as AddBlinkWalletEvent.Success
        assertEquals("My Wallet", event.connection.alias)
        assertEquals("full_key", repository.lastApiKey)
        assertEquals("My Wallet", repository.lastAlias)

        context.viewModel.clear()
    }

    @Test
    fun submitWithReadOnlyApiKeyShowsPermissionDeniedError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val context = createTestContext(
            repository = FakeBlinkWalletAccountRepository(
                error = AppError.BlinkError(BlinkErrorType.PermissionDenied)
            ),
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
        repository: FakeBlinkWalletAccountRepository = FakeBlinkWalletAccountRepository(),
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    ): TestContext {
        val viewModel = AddBlinkWalletViewModel(
            connectBlinkWallet = ConnectBlinkWalletUseCase(repository),
            dispatcher = dispatcher
        )
        return TestContext(viewModel)
    }

    private data class TestContext(val viewModel: AddBlinkWalletViewModel)

    private class FakeBlinkWalletAccountRepository(private val error: AppError? = null) : BlinkWalletAccountRepository {
        var lastApiKey: String? = null
            private set
        var lastAlias: String? = null
            private set

        override suspend fun connect(apiKey: String, alias: String): WalletConnection {
            error?.let { throw AppErrorException(it) }
            lastApiKey = apiKey
            lastAlias = alias
            return WalletConnection(
                walletPublicKey = "blink-test-wallet",
                alias = alias,
                type = WalletType.BLINK
            )
        }

        override suspend fun getCachedDefaultWalletId(walletId: String): String? = error("Not used by AddBlinkWalletViewModel")

        override suspend fun refreshDefaultWalletId(walletId: String): String = error("Not used by AddBlinkWalletViewModel")
    }
}
