package xyz.lilsus.papp.data.blink

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
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkErrorType

class BlinkApiClientTest {

    @Test
    fun fetchAuthorizationScopesReturnsScopes() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "data": {
                        "authorization": {
                            "scopes": ["READ", "WRITE", "RECEIVE"]
                        }
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val scopes = client.fetchAuthorizationScopes("test-api-key")

        assertEquals(listOf("READ", "WRITE", "RECEIVE"), scopes)
    }

    @Test
    fun fetchAuthorizationScopesReturnsEmptyListWhenNoScopes() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "data": {
                        "authorization": {
                            "scopes": []
                        }
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val scopes = client.fetchAuthorizationScopes("test-api-key")

        assertEquals(emptyList(), scopes)
    }

    @Test
    fun fetchAuthorizationScopesThrowsOnInvalidApiKey() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Unauthorized",
                status = HttpStatusCode.Unauthorized
            )
        }
        val client = createClient(mockEngine)

        val exception = assertFailsWith<AppErrorException> {
            client.fetchAuthorizationScopes("invalid-key")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKey, error.type)
    }

    @Test
    fun fetchDefaultWalletIdReturnsIdOnValidResponse() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "data": {
                        "me": {
                            "defaultAccount": {
                                "defaultWallet": { "id": "wallet-123" }
                            }
                        }
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val walletId = client.fetchDefaultWalletId("test-api-key")

        assertEquals("wallet-123", walletId)
    }

    @Test
    fun payInvoiceReturnsSuccessOnValidResponse() = runTest {
        val mockEngine = MockEngine { _ ->
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
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val result = client.payInvoice("test-api-key", "wallet-123", "lnbc1test")

        assertEquals(BlinkPaymentResult.Success(), result)
    }

    @Test
    fun payInvoiceParsesFeeFromTransaction() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
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
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val result = client.payInvoice("test-api-key", "wallet-123", "lnbc1test")

        assertEquals(BlinkPaymentResult.Success(feesPaidMsats = 10_000L), result)
    }

    @Test
    fun payInvoiceReturnsPendingOnPendingStatus() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "data": {
                        "lnInvoicePaymentSend": {
                            "status": "PENDING",
                            "errors": []
                        }
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val result = client.payInvoice("test-api-key", "wallet-123", "lnbc1test")

        assertEquals(BlinkPaymentResult.Pending(), result)
    }

    @Test
    fun payInvoiceReturnsAlreadyPaidOnAlreadyPaidStatus() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "data": {
                        "lnInvoicePaymentSend": {
                            "status": "ALREADY_PAID",
                            "errors": []
                        }
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val result = client.payInvoice("test-api-key", "wallet-123", "lnbc1test")

        assertEquals(BlinkPaymentResult.AlreadyPaid(), result)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOn401() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Unauthorized",
                status = HttpStatusCode.Unauthorized
            )
        }
        val client = createClient(mockEngine)

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("invalid-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKey, error.type)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOnUnauthenticatedGraphQLError() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "errors": [{
                        "message": "Not authenticated",
                        "extensions": { "code": "UNAUTHENTICATED" }
                    }]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("invalid-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKey, error.type)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOnInsufficientBalance() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "data": {
                        "lnInvoicePaymentSend": {
                            "status": "FAILURE",
                            "errors": [{
                                "message": "Insufficient balance",
                                "code": "INSUFFICIENT_BALANCE"
                            }]
                        }
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("test-api-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InsufficientBalance, error.type)
    }

    @Test
    fun payInvoiceThrowsPaymentRejectedOnUnknownOperationError() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "data": {
                        "lnInvoicePaymentSend": {
                            "status": "FAILURE",
                            "errors": [{
                                "message": "Some unknown error",
                                "code": "UNKNOWN_CODE"
                            }]
                        }
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("test-api-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.PaymentRejected)
        // Unknown errors preserve the original code and message
        assertEquals("UNKNOWN_CODE", error.code)
        assertEquals("Some unknown error", error.message)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOnPermissionDenied() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "errors": [{
                        "message": "not authorized to execute mutations",
                        "extensions": { "code": "AuthorizationError" }
                    }]
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("test-api-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.PermissionDenied, error.type)
    }

    @Test
    fun payNoAmountInvoiceReturnsSuccessOnValidResponse() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{
                    "data": {
                        "lnNoAmountInvoicePaymentSend": {
                            "status": "SUCCESS",
                            "errors": []
                        }
                    }
                }""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        val result = client.payNoAmountInvoice("test-api-key", "wallet-123", "lnbc1test", 1000)

        assertEquals(BlinkPaymentResult.Success(), result)
    }

    @Test
    fun payInvoiceIncludesWalletIdInRequestBody() = runTest {
        var capturedRequestBody: String? = null
        val mockEngine = MockEngine { request ->
            capturedRequestBody = (request.body as TextContent).text
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
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)

        client.payInvoice("test-api-key", "wallet-abc", "lnbc1test")

        val body = capturedRequestBody ?: error("Expected request body to be captured")
        val jsonBody = Json.parseToJsonElement(body).jsonObject
        val input = jsonBody["variables"]!!.jsonObject["input"]!!.jsonObject
        assertEquals("wallet-abc", input["walletId"]!!.jsonPrimitive.content)
    }

    private fun createClient(engine: MockEngine): BlinkApiClient {
        val httpClient = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        return BlinkApiClient(httpClient)
    }
}
