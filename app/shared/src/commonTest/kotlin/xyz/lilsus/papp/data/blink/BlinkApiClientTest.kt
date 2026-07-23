package xyz.lilsus.papp.data.blink

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import fr.acinq.lightning.utils.msat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.data.blink.graphql.LnInvoicePaymentSendMutation
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkErrorType
import xyz.lilsus.papp.testHash

class BlinkApiClientTest {

    @Test
    fun fetchAuthorizationScopesReturnsScopes() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "authorization": {
                        "scopes": ["READ", "WRITE", "RECEIVE"]
                    }
                }
            }"""
        )

        val scopes = client.fetchAuthorizationScopes("test-api-key")

        assertEquals(listOf("READ", "WRITE", "RECEIVE"), scopes)
    }

    @Test
    fun fetchAuthorizationScopesReturnsEmptyListWhenNoScopes() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "authorization": {
                        "scopes": []
                    }
                }
            }"""
        )

        val scopes = client.fetchAuthorizationScopes("test-api-key")

        assertEquals(emptyList(), scopes)
    }

    @Test
    fun fetchAuthorizationScopesThrowsOnInvalidApiKey() = runTest {
        val client = createClient { request -> request.httpErrorResponse(401, "Unauthorized") }

        val exception = assertFailsWith<AppErrorException> {
            client.fetchAuthorizationScopes("invalid-key")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKey, error.type)
    }

    @Test
    fun fetchDefaultWalletIdReturnsIdOnValidResponse() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "me": {
                        "defaultAccount": {
                            "defaultWallet": { "id": "wallet-123" }
                        }
                    }
                }
            }"""
        )

        val walletId = client.fetchDefaultWalletId("test-api-key")

        assertEquals("wallet-123", walletId)
    }

    @Test
    fun fetchContactsReturnsBlinkContacts() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "me": {
                        "contacts": [
                            {
                                "alias": "Alice",
                                "handle": "alice",
                                "transactionsCount": 3
                            },
                            {
                                "alias": null,
                                "handle": "bob@example.com",
                                "transactionsCount": 1
                            }
                        ]
                    }
                }
            }"""
        )

        val contacts = client.fetchContacts("test-api-key")

        assertEquals(2, contacts.size)
        assertEquals("alice", contacts[0].handle)
        assertEquals("Alice", contacts[0].alias)
        assertEquals(3, contacts[0].transactionsCount)
        assertEquals("blink.sv", contacts[0].lightningAddressDomain)
        assertEquals("bob@example.com", contacts[1].handle)
        assertEquals(null, contacts[1].alias)
    }

    @Test
    fun payInvoiceParsesFeeFromTransaction() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "SUCCESS",
                        "errors": [],
                        "transaction": {
                            "__typename": "Transaction",
                            "status": "SUCCESS",
                            "direction": "SEND",
                            "settlementFee": -10,
                            "settlementCurrency": "BTC",
                            "settlementVia": {
                                "__typename": "SettlementViaLn",
                                "preImage": "$TEST_PREIMAGE"
                            }
                        }
                    }
                }
            }"""
        )

        val result = client.payInvoice("test-api-key", "wallet-123", "lnbc1test")

        assertEquals(
            BlinkPaymentResult.Success(
                feesPaid = 10_000L.msat,
                preimage = testHash(TEST_PREIMAGE)
            ),
            result
        )
    }

    @Test
    fun payInvoiceReturnsPendingOnPendingStatus() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "PENDING",
                        "errors": [],
                        "transaction": null
                    }
                }
            }"""
        )

        val result = client.payInvoice("test-api-key", "wallet-123", "lnbc1test")

        assertEquals(BlinkPaymentResult.Pending(), result)
    }

    @Test
    fun payInvoiceReturnsAlreadyPaidOnAlreadyPaidStatus() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "ALREADY_PAID",
                        "errors": [],
                        "transaction": null
                    }
                }
            }"""
        )

        val result = client.payInvoice("test-api-key", "wallet-123", "lnbc1test")

        assertEquals(BlinkPaymentResult.AlreadyPaid(), result)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOn401() = runTest {
        val client = createClient { request -> request.httpErrorResponse(401, "Unauthorized") }

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("invalid-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKey, error.type)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOnUnauthenticatedGraphQLError() = runTest {
        val client = createClient(
            responseJson = """{
                "errors": [{
                    "message": "Not authenticated",
                    "extensions": { "code": "UNAUTHENTICATED" }
                }]
            }"""
        )

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("invalid-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKey, error.type)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOnInsufficientBalance() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "FAILURE",
                        "errors": [{
                            "message": "Insufficient balance",
                            "code": "INSUFFICIENT_BALANCE"
                        }],
                        "transaction": null
                    }
                }
            }"""
        )

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("test-api-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InsufficientBalance, error.type)
    }

    @Test
    fun payInvoiceThrowsPaymentRejectedOnUnknownOperationError() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "FAILURE",
                        "errors": [{
                            "message": "Some unknown error",
                            "code": "UNKNOWN_CODE"
                        }],
                        "transaction": null
                    }
                }
            }"""
        )

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("test-api-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.PaymentRejected)
        assertEquals("UNKNOWN_CODE", error.code)
        assertEquals("Some unknown error", error.message)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOnPermissionDenied() = runTest {
        val client = createClient(
            responseJson = """{
                "errors": [{
                    "message": "not authorized to execute mutations",
                    "extensions": { "code": "AuthorizationError" }
                }]
            }"""
        )

        val exception = assertFailsWith<AppErrorException> {
            client.payInvoice("test-api-key", "wallet-123", "lnbc1test")
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.PermissionDenied, error.type)
    }

    @Test
    fun payNoAmountInvoiceReturnsSuccessOnValidResponse() = runTest {
        val client = createClient(
            responseJson = """{
                "data": {
                    "lnNoAmountInvoicePaymentSend": {
                        "status": "SUCCESS",
                        "errors": [],
                        "transaction": null
                    }
                }
            }"""
        )

        val result = client.payNoAmountInvoice("test-api-key", "wallet-123", "lnbc1test", 1000)

        assertEquals(BlinkPaymentResult.Success(), result)
    }

    @Test
    fun payInvoiceIncludesWalletIdInOperationInput() = runTest {
        var capturedMutation: LnInvoicePaymentSendMutation? = null
        val client = createClient { request ->
            capturedMutation = request.operation as LnInvoicePaymentSendMutation
            request.responseFromJson(
                """{
                    "data": {
                        "lnInvoicePaymentSend": {
                            "status": "SUCCESS",
                            "errors": [],
                            "transaction": null
                        }
                    }
                }"""
            )
        }

        client.payInvoice("test-api-key", "wallet-abc", "lnbc1test")

        val mutation = capturedMutation ?: error("Expected payment mutation to be captured")
        assertEquals("wallet-abc", mutation.input.walletId)
        assertEquals("lnbc1test", mutation.input.paymentRequest)
    }

    private fun createClient(responseJson: String): BlinkApiClient = createClient { request -> request.responseFromJson(responseJson) }

    private fun createClient(handler: (ApolloRequest<*>) -> ApolloResponse<*>): BlinkApiClient {
        val transport = BlinkApolloTestTransport(handler)
        return BlinkApiClient(createBlinkApolloTestClient(transport))
    }
}

private const val TEST_PREIMAGE =
    "1111111111111111111111111111111111111111111111111111111111111111"
