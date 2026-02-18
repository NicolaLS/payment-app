package xyz.lilsus.papp.presentation.settings.addblink

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import xyz.lilsus.papp.data.blink.BlinkApiClient
import xyz.lilsus.papp.data.blink.BlinkCredentialStore
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.BlinkErrorType

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

    @Test
    fun submitWithReadOnlyApiKeyShowsPermissionDeniedError() = kotlinx.coroutines.runBlocking {
        val context = createTestContext(authorizationResponse = READ_ONLY_AUTH_RESPONSE)

        context.viewModel.updateAlias("My Wallet")
        context.viewModel.updateApiKey("read_only_key")
        context.viewModel.submit()

        // Wait for async operation to complete (real time delay needed since ViewModel uses Dispatchers.Default)
        kotlinx.coroutines.delay(200)

        val state = context.viewModel.uiState.value
        assertNotNull(state.error)
        val error = state.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.PermissionDenied, error.type)
        assertFalse(state.isSaving)

        context.viewModel.clear()
    }

    private fun createTestContext(authorizationResponse: String = DEFAULT_AUTH_RESPONSE): TestContext {
        val settings = MapSettings()
        val walletSettingsRepository = WalletSettingsRepositoryImpl(
            settings = settings
        )
        val credentialStore = BlinkCredentialStore(settings)
        val apiClient = createMockApiClient(authorizationResponse)
        val viewModel = AddBlinkWalletViewModel(
            walletSettingsRepository = walletSettingsRepository,
            credentialStore = credentialStore,
            apiClient = apiClient,
            dispatcher = Dispatchers.Default
        )
        return TestContext(viewModel, walletSettingsRepository, credentialStore, apiClient)
    }

    private fun createMockApiClient(authorizationResponse: String): BlinkApiClient {
        val mockEngine = MockEngine { request ->
            val body = (request.body as TextContent).text
            val responseBody = if (body.contains("authorization")) {
                authorizationResponse
            } else {
                DEFAULT_WALLET_RESPONSE
            }
            respond(
                content = responseBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return BlinkApiClient(httpClient)
    }

    private data class TestContext(
        val viewModel: AddBlinkWalletViewModel,
        val walletSettingsRepository: WalletSettingsRepositoryImpl,
        val credentialStore: BlinkCredentialStore,
        val apiClient: BlinkApiClient
    )

    companion object {
        private const val DEFAULT_AUTH_RESPONSE = """{
            "data": {
                "authorization": {
                    "scopes": ["READ", "WRITE", "RECEIVE"]
                }
            }
        }"""

        private const val READ_ONLY_AUTH_RESPONSE = """{
            "data": {
                "authorization": {
                    "scopes": ["READ"]
                }
            }
        }"""

        private const val DEFAULT_WALLET_RESPONSE = """{
            "data": {
                "me": {
                    "defaultAccount": {
                        "defaultWallet": {
                            "id": "wallet-123"
                        }
                    }
                }
            }
        }"""
    }
}
