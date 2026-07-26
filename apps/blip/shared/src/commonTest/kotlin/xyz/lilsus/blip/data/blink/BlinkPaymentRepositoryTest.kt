package xyz.lilsus.blip.data.blink

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.russhwolf.settings.MapSettings
import fr.acinq.lightning.utils.msat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import xyz.lilsus.blip.data.blink.graphql.DefaultWalletIdQuery
import xyz.lilsus.blip.data.blink.graphql.LnInvoicePaymentSendMutation
import xyz.lilsus.blip.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.blip.domain.model.AppError
import xyz.lilsus.blip.domain.model.AppErrorException
import xyz.lilsus.blip.domain.model.BlinkErrorType
import xyz.lilsus.blip.domain.model.PayInvoiceRequestState
import xyz.lilsus.blip.domain.model.WalletConnection
import xyz.lilsus.blip.domain.model.WalletType
import xyz.lilsus.blip.domain.repository.WalletSettingsRepository
import xyz.lilsus.blip.platform.NetworkConnectivity
import xyz.lilsus.blip.testHash
import xyz.lilsus.blip.testInvoice

/**
 * Tests for BlinkPaymentRepository.
 * Verifies payment routing for different invoice types and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlinkPaymentRepositoryTest {

    @Test
    fun payInvoiceWithAmountSucceeds() = runTest {
        val context = createTestContext(
            paymentResponseJson = """{
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

        val result = context.repository.payInvoice(testInvoice("lnbc1000n1test"))

        assertNotNull(result)
        assertEquals(testHash(TEST_PREIMAGE), result.preimage)
        assertEquals(10_000L.msat, result.feesPaid)
    }

    @Test
    fun payInvoiceReturnsAlreadyPaidResultWithoutFees() = runTest {
        val context = createTestContext(
            paymentResponseJson = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "ALREADY_PAID",
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

        val result = context.repository.payInvoice(testInvoice("lnbc1000n1test"))

        assertNotNull(result)
        assertTrue(result.wasAlreadyPaid)
        assertEquals(testHash(TEST_PREIMAGE), result.preimage)
        assertNull(result.feesPaid)
    }

    @Test
    fun payInvoiceUsesDefaultWalletIdFromApi() = runTest {
        var capturedBlinkWalletId: String? = null
        val context = createTestContextWithHandler { request ->
            when (val operation = request.operation) {
                is DefaultWalletIdQuery -> request.responseFromJson(defaultWalletResponseJson())

                is LnInvoicePaymentSendMutation -> {
                    capturedBlinkWalletId = operation.input.walletId
                    request.responseFromJson(paymentSuccessResponseJson())
                }

                else -> error("Unexpected Blink operation: ${operation.name()}")
            }
        }

        context.repository.payInvoice(testInvoice("lnbc1000n1test"))

        assertEquals(TEST_BLINK_DEFAULT_WALLET_ID, capturedBlinkWalletId)
    }

    @Test
    fun payInvoiceUsesStoredDefaultWalletIdWhenAvailable() = runTest {
        var capturedBlinkWalletId: String? = null
        var defaultWalletQuerySeen = false
        val context = createTestContextWithHandler { request ->
            when (val operation = request.operation) {
                is DefaultWalletIdQuery -> {
                    defaultWalletQuerySeen = true
                    request.responseFromJson(defaultWalletResponseJson())
                }

                is LnInvoicePaymentSendMutation -> {
                    capturedBlinkWalletId = operation.input.walletId
                    request.responseFromJson(paymentSuccessResponseJson())
                }

                else -> error("Unexpected Blink operation: ${operation.name()}")
            }
        }
        context.credentialStore.storeDefaultWalletId(TEST_BLINK_DEFAULT_WALLET_ID)

        context.repository.payInvoice(testInvoice("lnbc1000n1test"))

        assertEquals(TEST_BLINK_DEFAULT_WALLET_ID, capturedBlinkWalletId)
        assertFalse(defaultWalletQuerySeen)
    }

    @Test
    fun payNoAmountInvoiceWithUserProvidedAmountSucceeds() = runTest {
        val context = createTestContext(
            paymentResponseJson = """{
                "data": {
                    "lnNoAmountInvoicePaymentSend": {
                        "status": "SUCCESS",
                        "errors": [],
                        "transaction": {
                            "__typename": "Transaction",
                            "status": "SUCCESS",
                            "direction": "SEND",
                            "settlementFee": -2,
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

        // 1000 msats = 1 sat (rounded up)
        val result = context.repository.payInvoice(
            testInvoice("lnbc1test", amountMsats = null),
            amount = 1_000L.msat
        )

        assertNotNull(result)
        assertEquals(testHash(TEST_PREIMAGE), result.preimage)
        assertEquals(2_000L.msat, result.feesPaid)
    }

    @Test
    fun payInvoiceThrowsMissingWalletConnectionWhenNoWalletIsConnected() = runTest {
        val context = createTestContext(paymentResponseJson = """{"data": {}}""")
        context.walletSettingsRepository.clearWalletConnection()

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(testInvoice("lnbc1test"))
        }

        assertTrue(exception.error is AppError.MissingWalletConnection)
    }

    @Test
    fun payInvoiceThrowsAuthenticationFailureWhenApiKeyNotFound() = runTest {
        val context = createTestContext(paymentResponseJson = """{"data": {}}""")
        context.credentialStore.clear()

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(testInvoice("lnbc1test"))
        }

        assertTrue(exception.error is AppError.AuthenticationFailure)
    }

    @Test
    fun payInvoiceReturnsUnconfirmedOnNetworkError() = runTest {
        val context = createTestContextWithHandler { request ->
            when (val operation = request.operation) {
                is DefaultWalletIdQuery -> request.responseFromJson(defaultWalletResponseJson())
                is LnInvoicePaymentSendMutation -> request.httpErrorResponse(503, "Service Unavailable")
                else -> error("Unexpected Blink operation: ${operation.name()}")
            }
        }

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(testInvoice("lnbc1test"))
        }

        assertTrue(exception.error is AppError.PaymentUnconfirmed)
    }

    @Test
    fun payInvoiceThrowsBlinkErrorOnInsufficientBalance() = runTest {
        val context = createTestContext(
            paymentResponseJson = """{
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
            context.repository.payInvoice(testInvoice("lnbc1test"))
        }

        val error = exception.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InsufficientBalance, error.type)
    }

    @Test
    fun startPayInvoiceRemovesWalletOnInvalidApiKey() = runTest {
        val context = createTestContextWithHandler { request ->
            request.httpErrorResponse(401, "Unauthorized")
        }

        assertTrue(context.credentialStore.hasApiKey())
        context.credentialStore.storeDefaultWalletId(TEST_BLINK_DEFAULT_WALLET_ID)

        val request = context.repository.startPayInvoiceRequest(testInvoice("lnbc1test"), null)

        while (request.state.value is PayInvoiceRequestState.Loading) {
            delay(10)
        }

        val state = request.state.value
        assertTrue(state is PayInvoiceRequestState.Failure)
        val error = state.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKeyWalletRemoved, error.type)

        assertFalse(context.credentialStore.hasApiKey())
        assertNull(context.credentialStore.getDefaultWalletId())
        assertNull(context.walletSettingsRepository.getWalletConnection())
    }

    @Test
    fun lookupPaymentUsesConnectedBlinkCredentials() = runTest {
        var capturedApiKey: String? = null
        val context = createTestContextWithHandler { request ->
            capturedApiKey = request.apiKeyHeader()
            request.responseFromJson(transactionsSuccessResponseJson())
        }

        context.repository.lookupPayment(paymentHash = testHash("test-payment-hash"))

        assertEquals(TEST_API_KEY, capturedApiKey)
    }

    private suspend fun createTestContext(paymentResponseJson: String): TestContext = createTestContextWithHandler { request ->
        if (request.operation is DefaultWalletIdQuery) {
            request.responseFromJson(defaultWalletResponseJson())
        } else {
            request.responseFromJson(paymentResponseJson)
        }
    }

    private suspend fun createTestContextWithHandler(handler: (ApolloRequest<*>) -> ApolloResponse<*>): TestContext {
        val settings = MapSettings()
        val credentialStore = BlinkCredentialStore(settings)
        val transport = BlinkApolloTestTransport(handler)
        val apiClient = BlinkApiClient(createBlinkApolloTestClient(transport))
        val walletSettingsRepository = WalletSettingsRepositoryImpl(
            settings = MapSettings(),
            onWalletRemoved = { wallet ->
                if (wallet.isBlink) credentialStore.clear()
            }
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val networkConnectivity = AlwaysOnlineNetworkConnectivity()
        val repository = BlinkPaymentRepository(
            apiClient,
            credentialStore,
            walletSettingsRepository,
            networkConnectivity,
            scope
        )

        credentialStore.storeApiKey(TEST_API_KEY)
        walletSettingsRepository.saveWalletConnection(
            WalletConnection(
                alias = "test blink wallet",
                walletPublicKey = TEST_WALLET_ID,
                type = WalletType.BLINK
            )
        )

        return TestContext(
            repository = repository,
            credentialStore = credentialStore,
            walletSettingsRepository = walletSettingsRepository
        )
    }

    private data class TestContext(
        val repository: BlinkPaymentRepository,
        val credentialStore: BlinkCredentialStore,
        val walletSettingsRepository: WalletSettingsRepository
    )

    private class AlwaysOnlineNetworkConnectivity : NetworkConnectivity {
        override fun isNetworkAvailable(): Boolean = true
    }

    companion object {
        private const val TEST_WALLET_ID = "blink-test-wallet-123"
        private const val TEST_API_KEY = "blink_test_api_key"
        private const val TEST_BLINK_DEFAULT_WALLET_ID = "wallet-123"
        private const val TEST_PREIMAGE =
            "1111111111111111111111111111111111111111111111111111111111111111"

        private fun defaultWalletResponseJson(): String = """{
            "data": {
                "me": {
                    "defaultAccount": {
                        "defaultWallet": { "id": "$TEST_BLINK_DEFAULT_WALLET_ID" }
                    }
                }
            }
        }"""

        private fun paymentSuccessResponseJson(): String = """{
            "data": {
                "lnInvoicePaymentSend": {
                    "status": "SUCCESS",
                    "errors": [],
                    "transaction": null
                }
            }
        }"""

        private fun transactionsSuccessResponseJson(): String = """{
            "data": {
                "me": {
                    "defaultAccount": {
                        "wallets": [{
                            "__typename": "BTCWallet",
                            "transactionsByPaymentHash": [{
                                "__typename": "Transaction",
                                "status": "SUCCESS",
                                "direction": "SEND",
                                "settlementFee": -3,
                                "settlementCurrency": "BTC",
                                "settlementVia": {
                                    "__typename": "SettlementViaLn",
                                    "preImage": "$TEST_PREIMAGE"
                                }
                            }]
                        }]
                    }
                }
            }
        }"""
    }
}
