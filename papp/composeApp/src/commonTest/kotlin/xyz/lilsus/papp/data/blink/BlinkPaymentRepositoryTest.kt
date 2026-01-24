package xyz.lilsus.papp.data.blink

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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkErrorType

/**
 * Tests for BlinkPaymentRepository.
 * Verifies payment routing for different invoice types and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlinkPaymentRepositoryTest {

    @Test
    fun payInvoiceWithAmountSucceeds() = runTest {
        val context = createTestContext(
            responseBody = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "SUCCESS",
                        "errors": [],
                        "transaction": {
                            "settlementFee": -10,
                            "settlementCurrency": "BTC"
                        }
                    }
                }
            }"""
        )

        val result = context.repository.payInvoice("lnbc1000n1test")

        assertNotNull(result)
        assertNull(result.preimage)
        assertEquals(10_000L, result.feesPaidMsats)
    }

    @Test
    fun payInvoiceUsesDefaultWalletIdFromApi() = runTest {
        var paymentRequestBody: String? = null
        var callCount = 0
        val mockEngine = MockEngine { request ->
            callCount += 1
            if (callCount == 1) {
                respond(
                    content = defaultWalletResponseBody(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString()
                    )
                )
            } else {
                paymentRequestBody = (request.body as TextContent).text
                respond(
                    content = """{
                        "data": {
                            "lnInvoicePaymentSend": {
                                "status": "SUCCESS",
                                "errors": []
                            }
                        }
                    }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString()
                    )
                )
            }
        }
        val context = createTestContextWithEngine(mockEngine)

        context.repository.payInvoice("lnbc1000n1test")

        val body = paymentRequestBody ?: error("Expected payment request body to be captured")
        val jsonBody = Json.parseToJsonElement(body).jsonObject
        val input = jsonBody["variables"]!!.jsonObject["input"]!!.jsonObject
        assertEquals(TEST_BLINK_DEFAULT_WALLET_ID, input["walletId"]!!.jsonPrimitive.content)
    }

    @Test
    fun payNoAmountInvoiceWithUserProvidedAmountSucceeds() = runTest {
        val context = createTestContext(
            responseBody = """{
                "data": {
                    "lnNoAmountInvoicePaymentSend": {
                        "status": "SUCCESS",
                        "errors": [],
                        "transaction": {
                            "settlementFee": -2,
                            "settlementCurrency": "BTC"
                        }
                    }
                }
            }"""
        )

        // 1000 msats = 1 sat (rounded up)
        val result = context.repository.payInvoice("lnbc1test", amountMsats = 1000L)

        assertNotNull(result)
        assertNull(result.preimage)
        assertEquals(2_000L, result.feesPaidMsats)
    }

    @Test
    fun payInvoiceThrowsMissingWalletConnectionWhenNoActiveWallet() = runTest {
        val context = createTestContext(
            responseBody = """{"data": {}}"""
        )
        // Don't set active wallet
        context.repository.setActiveWallet(null)

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice("lnbc1test")
        }

        assertTrue(exception.error is AppError.MissingWalletConnection)
    }

    @Test
    fun payInvoiceThrowsAuthenticationFailureWhenApiKeyNotFound() = runTest {
        val context = createTestContext(
            responseBody = """{"data": {}}"""
        )
        // Set active wallet but don't store API key
        context.credentialStore.removeApiKey(TEST_WALLET_ID)
        context.repository.setActiveWallet(TEST_WALLET_ID)

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice("lnbc1test")
        }

        assertTrue(exception.error is AppError.AuthenticationFailure)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOn401() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Unauthorized",
                status = HttpStatusCode.Unauthorized
            )
        }
        val context = createTestContextWithEngine(mockEngine)

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice("lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKey, (error as AppError.BlinkError).type)
    }

    @Test
    fun payInvoiceThrowsNetworkUnavailableOnNetworkError() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Service Unavailable",
                status = HttpStatusCode.ServiceUnavailable
            )
        }
        val context = createTestContextWithEngine(mockEngine)

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice("lnbc1test")
        }

        assertTrue(exception.error is AppError.NetworkUnavailable)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOnInsufficientBalance() = runTest {
        val context = createTestContext(
            responseBody = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "FAILURE",
                        "errors": [{
                            "message": "Insufficient balance",
                            "code": "INSUFFICIENT_BALANCE"
                        }]
                    }
                }
            }"""
        )

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice("lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InsufficientBalance, (error as AppError.BlinkError).type)
    }

    private fun createTestContext(responseBody: String): TestContext {
        var callCount = 0
        val mockEngine = MockEngine { _ ->
            callCount += 1
            val body = if (callCount == 1) defaultWalletResponseBody() else responseBody
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return createTestContextWithEngine(mockEngine)
    }

    private fun createTestContextWithEngine(engine: MockEngine): TestContext {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val settings = MapSettings()
        val credentialStore = BlinkCredentialStore(settings)
        val apiClient = BlinkApiClient(httpClient)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val repository = BlinkPaymentRepository(apiClient, credentialStore, scope)

        // Set up default test wallet
        credentialStore.storeApiKey(TEST_WALLET_ID, TEST_API_KEY)
        repository.setActiveWallet(TEST_WALLET_ID)

        return TestContext(repository, credentialStore, apiClient)
    }

    private data class TestContext(
        val repository: BlinkPaymentRepository,
        val credentialStore: BlinkCredentialStore,
        val apiClient: BlinkApiClient
    )

    companion object {
        private const val TEST_WALLET_ID = "blink-test-wallet-123"
        private const val TEST_API_KEY = "blink_test_api_key"
        private const val TEST_BLINK_DEFAULT_WALLET_ID = "wallet-123"
    }

    private fun defaultWalletResponseBody(): String = """{
        "data": {
            "me": {
                "defaultAccount": {
                    "defaultWallet": { "id": "$TEST_BLINK_DEFAULT_WALLET_ID" }
                }
            }
        }
    }"""
}
