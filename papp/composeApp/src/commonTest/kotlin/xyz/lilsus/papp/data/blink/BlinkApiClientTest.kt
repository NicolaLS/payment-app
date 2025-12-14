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

class BlinkApiClientTest {

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
    fun payInvoiceThrowsAuthenticationFailureOn401() = runTest {
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

        assertTrue(exception.error is AppError.AuthenticationFailure)
    }

    @Test
    fun payInvoiceThrowsAuthenticationFailureOnUnauthenticatedGraphQLError() = runTest {
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

        assertTrue(exception.error is AppError.AuthenticationFailure)
    }

    @Test
    fun payInvoiceThrowsPaymentRejectedOnOperationError() = runTest {
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
        assertTrue(error is AppError.PaymentRejected)
        assertEquals("INSUFFICIENT_BALANCE", (error as AppError.PaymentRejected).code)
        assertEquals("Insufficient balance", error.message)
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
