package xyz.lilsus.blip.integration.blink

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class BlinkWalletPaymentTest {
    @Test
    fun pendingApiResponseRemainsPending() = runBlocking {
        val wallet =
            createWallet(
                responseJson = paymentResponse(status = "PENDING")
            )

        val result = wallet.submitPayment(BlinkPaymentRequest("lnbc1test"))

        assertEquals(BlinkPaymentOutcome.Pending, result)
    }

    @Test
    fun rejectedApiResponseIsDefinitiveFailure() = runBlocking {
        val wallet =
            createWallet(
                responseJson =
                    paymentResponse(
                        status = "FAILURE",
                        errors =
                            """
                    [{
                        "code": "INSUFFICIENT_BALANCE",
                        "message": "Insufficient balance"
                    }]
                            """.trimIndent()
                    )
            )

        val result = wallet.submitPayment(BlinkPaymentRequest("lnbc1test"))

        assertEquals(
            BlinkPaymentOutcome.DefinitiveFailure(
                BlinkApiError.BlinkError(BlinkErrorType.InsufficientBalance)
            ),
            result
        )
    }

    @Test
    fun unknownApiStatusHasUnknownPaymentStatus() = runBlocking {
        val wallet =
            createWallet(
                responseJson = paymentResponse(status = "FUTURE_STATUS")
            )

        val result = wallet.submitPayment(BlinkPaymentRequest("lnbc1test"))

        assertIs<BlinkPaymentOutcome.StatusUnknown>(result)
        Unit
    }

    @Test
    fun disconnectedWalletDoesNotSubmitPayment() = runBlocking {
        var requestCount = 0
        val client =
            createClient {
                requestCount += 1
                it.responseFromJson(paymentResponse(status = "SUCCESS"))
            }
        val wallet =
            BlinkWallet(
                apiClient = client,
                credentialStore = BlinkCredentialStore(MapSettings()),
                isNetworkAvailable = { true }
            )

        val result = wallet.submitPayment(BlinkPaymentRequest("lnbc1test"))

        assertEquals(
            BlinkPaymentOutcome.DefinitiveFailure(BlinkApiError.MissingWalletConnection),
            result
        )
        assertEquals(0, requestCount)
    }

    private fun createWallet(responseJson: String): BlinkWallet {
        val store = BlinkCredentialStore(MapSettings())
        store.save(
            BlinkCredentials(
                apiKey = "test-api-key",
                defaultWalletId = "wallet-123",
                alias = "Test"
            )
        )
        return BlinkWallet(
            apiClient = createClient { it.responseFromJson(responseJson) },
            credentialStore = store,
            isNetworkAvailable = { true }
        )
    }

    private fun createClient(handler: (ApolloRequest<*>) -> ApolloResponse<*>): BlinkApiClient = BlinkApiClient(
        createBlinkApolloTestClient(BlinkApolloTestTransport(handler))
    )

    private fun paymentResponse(status: String, errors: String = "[]"): String =
        """
        {
            "data": {
                "lnInvoicePaymentSend": {
                    "status": "$status",
                    "errors": $errors,
                    "transaction": null
                }
            }
        }
        """.trimIndent()
}
