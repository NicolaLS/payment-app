package xyz.lilsus.papp.presentation.settings.addblink

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import xyz.lilsus.papp.data.blink.BlinkCredentialStore
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.domain.model.AppError

/**
 * Tests for AddBlinkWalletViewModel.
 * Tests focus on synchronous state updates to avoid coroutine scope issues.
 */
class AddBlinkWalletViewModelTest {

    @Test
    fun initialStateHasEmptyFields() {
        val context = createTestContext()
        val state = context.viewModel.uiState.value

        assertEquals("", state.alias)
        assertEquals("", state.apiKey)
        assertFalse(state.isSaving)
        assertEquals(null, state.error)
        assertFalse(state.canSubmit)

        context.viewModel.clear()
    }

    @Test
    fun updateAliasUpdatesState() {
        val context = createTestContext()

        context.viewModel.updateAlias("My Blink Wallet")

        val state = context.viewModel.uiState.value
        assertEquals("My Blink Wallet", state.alias)

        context.viewModel.clear()
    }

    @Test
    fun updateApiKeyUpdatesState() {
        val context = createTestContext()

        context.viewModel.updateApiKey("blink_test_key_123")

        val state = context.viewModel.uiState.value
        assertEquals("blink_test_key_123", state.apiKey)

        context.viewModel.clear()
    }

    @Test
    fun canSubmitIsTrueWhenBothFieldsAreFilled() {
        val context = createTestContext()

        context.viewModel.updateAlias("My Wallet")
        context.viewModel.updateApiKey("blink_key")

        val state = context.viewModel.uiState.value
        assertTrue(state.canSubmit)

        context.viewModel.clear()
    }

    @Test
    fun canSubmitIsFalseWhenAliasIsEmpty() {
        val context = createTestContext()

        context.viewModel.updateAlias("")
        context.viewModel.updateApiKey("blink_key")

        val state = context.viewModel.uiState.value
        assertFalse(state.canSubmit)

        context.viewModel.clear()
    }

    @Test
    fun canSubmitIsFalseWhenApiKeyIsEmpty() {
        val context = createTestContext()

        context.viewModel.updateAlias("My Wallet")
        context.viewModel.updateApiKey("")

        val state = context.viewModel.uiState.value
        assertFalse(state.canSubmit)

        context.viewModel.clear()
    }

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
    fun updateAliasClearsError() {
        val context = createTestContext()

        // First trigger an error
        context.viewModel.updateAlias("   ")
        context.viewModel.updateApiKey("blink_key")
        context.viewModel.submit()
        assertNotNull(context.viewModel.uiState.value.error)

        // Now update alias - error should be cleared
        context.viewModel.updateAlias("Valid Alias")
        assertEquals(null, context.viewModel.uiState.value.error)

        context.viewModel.clear()
    }

    @Test
    fun updateApiKeyClearsError() {
        val context = createTestContext()

        // First trigger an error
        context.viewModel.updateAlias("My Wallet")
        context.viewModel.updateApiKey("   ")
        context.viewModel.submit()
        assertNotNull(context.viewModel.uiState.value.error)

        // Now update API key - error should be cleared
        context.viewModel.updateApiKey("valid_key")
        assertEquals(null, context.viewModel.uiState.value.error)

        context.viewModel.clear()
    }

    private fun createTestContext(): TestContext {
        val settings = MapSettings()
        val walletSettingsRepository = WalletSettingsRepositoryImpl(
            settings = settings,
            dispatcher = Dispatchers.Default
        )
        val credentialStore = BlinkCredentialStore(settings)
        val viewModel = AddBlinkWalletViewModel(
            walletSettingsRepository = walletSettingsRepository,
            credentialStore = credentialStore,
            dispatcher = Dispatchers.Default
        )
        return TestContext(viewModel, walletSettingsRepository, credentialStore)
    }

    private data class TestContext(
        val viewModel: AddBlinkWalletViewModel,
        val walletSettingsRepository: WalletSettingsRepositoryImpl,
        val credentialStore: BlinkCredentialStore
    )
}
