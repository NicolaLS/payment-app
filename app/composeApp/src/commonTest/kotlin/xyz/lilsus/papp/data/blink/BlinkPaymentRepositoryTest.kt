package xyz.lilsus.papp.data.blink

import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.ApolloResponse
import com.russhwolf.settings.MapSettings
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.data.blink.graphql.DefaultWalletIdQuery
import xyz.lilsus.papp.data.blink.graphql.LnInvoicePaymentSendMutation
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkErrorType
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletPaymentTarget
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.platform.NetworkConnectivity

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
    fun payInvoiceReturnsAlreadyPaidResultWithoutFees() = runTest {
        val context = createTestContext(
            paymentResponseJson = """{
                "data": {
                    "lnInvoicePaymentSend": {
                        "status": "ALREADY_PAID",
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
        assertTrue(result.wasAlreadyPaid)
        assertNull(result.feesPaidMsats)
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

        context.repository.payInvoice("lnbc1000n1test")

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
        context.credentialStore.storeDefaultWalletId(TEST_WALLET_ID, TEST_BLINK_DEFAULT_WALLET_ID)

        context.repository.payInvoice("lnbc1000n1test")

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
        val context = createTestContext(paymentResponseJson = """{"data": {}}""")
        context.repository.setActiveWallet(null)

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice("lnbc1test")
        }

        assertTrue(exception.error is AppError.MissingWalletConnection)
    }

    @Test
    fun payInvoiceThrowsAuthenticationFailureWhenApiKeyNotFound() = runTest {
        val context = createTestContext(paymentResponseJson = """{"data": {}}""")
        context.credentialStore.removeApiKey(TEST_WALLET_ID)
        context.repository.setActiveWallet(TEST_WALLET_ID)

        val exception = assertFailsWith<AppErrorException> {
            context.repository.payInvoice("lnbc1test")
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
            context.repository.payInvoice("lnbc1test")
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
            context.repository.payInvoice("lnbc1test")
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

        assertTrue(context.credentialStore.hasApiKey(TEST_WALLET_ID))
        context.credentialStore.storeDefaultWalletId(TEST_WALLET_ID, TEST_BLINK_DEFAULT_WALLET_ID)
        assertTrue(context.walletSettingsRepository.removedWallets.isEmpty())

        val request = context.repository.startPayInvoiceRequest("lnbc1test", null)

        while (request.state.value is PayInvoiceRequestState.Loading) {
            delay(10)
        }

        val state = request.state.value
        assertTrue(state is PayInvoiceRequestState.Failure)
        val error = state.error
        assertTrue(error is AppError.BlinkError)
        assertEquals(BlinkErrorType.InvalidApiKeyWalletRemoved, error.type)

        assertFalse(context.credentialStore.hasApiKey(TEST_WALLET_ID))
        assertNull(context.credentialStore.getDefaultWalletId(TEST_WALLET_ID))
        assertTrue(context.walletSettingsRepository.removedWallets.contains(TEST_WALLET_ID))
    }

    @Test
    fun lookupPaymentUsesProvidedWalletIdInsteadOfActiveWallet() = runTest {
        val wallet1Id = "wallet-1"
        val wallet2Id = "wallet-2"
        val wallet1ApiKey = "api-key-1"
        val wallet2ApiKey = "api-key-2"

        var capturedApiKey: String? = null
        val context = createTestContextWithHandler { request ->
            capturedApiKey = request.apiKeyHeader()
            request.responseFromJson(transactionsSuccessResponseJson())
        }

        context.credentialStore.storeApiKey(wallet1Id, wallet1ApiKey)
        context.credentialStore.storeApiKey(wallet2Id, wallet2ApiKey)
        context.repository.setActiveWallet(wallet1Id)

        context.repository.lookupPayment(
            paymentHash = "test-payment-hash",
            walletTarget = WalletPaymentTarget.Blink(wallet2Id)
        )

        assertEquals(wallet2ApiKey, capturedApiKey)
    }

    @Test
    fun lookupPaymentUsesActiveWalletWhenNoWalletUriProvided() = runTest {
        var capturedApiKey: String? = null
        val context = createTestContextWithHandler { request ->
            capturedApiKey = request.apiKeyHeader()
            request.responseFromJson(transactionsSuccessResponseJson())
        }

        context.repository.lookupPayment(
            paymentHash = "test-payment-hash",
            walletTarget = null
        )

        assertEquals(TEST_API_KEY, capturedApiKey)
    }

    @Test
    fun concurrentLookupsOnDifferentWalletsUsesCorrectApiKeys() = runTest {
        val wallet1Id = "wallet-1"
        val wallet2Id = "wallet-2"
        val wallet1ApiKey = "api-key-1"
        val wallet2ApiKey = "api-key-2"

        val capturedApiKeys = mutableListOf<String>()
        val context = createTestContextWithHandler { request ->
            capturedApiKeys.add(request.apiKeyHeader() ?: "missing")
            request.responseFromJson(transactionsSuccessResponseJson())
        }

        context.credentialStore.storeApiKey(wallet1Id, wallet1ApiKey)
        context.credentialStore.storeApiKey(wallet2Id, wallet2ApiKey)
        context.repository.setActiveWallet(wallet1Id)

        val lookup1 = async {
            context.repository.lookupPayment(
                paymentHash = "hash-1",
                walletTarget = WalletPaymentTarget.Blink(wallet1Id)
            )
        }
        val lookup2 = async {
            context.repository.lookupPayment(
                paymentHash = "hash-2",
                walletTarget = WalletPaymentTarget.Blink(wallet2Id)
            )
        }

        lookup1.await()
        lookup2.await()

        assertEquals(2, capturedApiKeys.size)
        assertTrue(capturedApiKeys.contains(wallet1ApiKey))
        assertTrue(capturedApiKeys.contains(wallet2ApiKey))
    }

    private fun createTestContext(paymentResponseJson: String): TestContext = createTestContextWithHandler { request ->
        if (request.operation is DefaultWalletIdQuery) {
            request.responseFromJson(defaultWalletResponseJson())
        } else {
            request.responseFromJson(paymentResponseJson)
        }
    }

    private fun createTestContextWithHandler(handler: (ApolloRequest<*>) -> ApolloResponse<*>): TestContext {
        val settings = MapSettings()
        val credentialStore = BlinkCredentialStore(settings)
        val transport = BlinkApolloTestTransport(handler)
        val apiClient = BlinkApiClient(createBlinkApolloTestClient(transport))
        val walletSettingsRepository = FakeWalletSettingsRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val networkConnectivity = AlwaysOnlineNetworkConnectivity()
        val repository = BlinkPaymentRepository(
            apiClient,
            credentialStore,
            walletSettingsRepository,
            networkConnectivity,
            scope
        )

        credentialStore.storeApiKey(TEST_WALLET_ID, TEST_API_KEY)
        repository.setActiveWallet(TEST_WALLET_ID)

        return TestContext(
            repository = repository,
            credentialStore = credentialStore,
            walletSettingsRepository = walletSettingsRepository
        )
    }

    private data class TestContext(
        val repository: BlinkPaymentRepository,
        val credentialStore: BlinkCredentialStore,
        val walletSettingsRepository: FakeWalletSettingsRepository
    )

    private class AlwaysOnlineNetworkConnectivity : NetworkConnectivity {
        override fun isNetworkAvailable(): Boolean = true
    }

    /**
     * Fake implementation of WalletSettingsRepository for testing.
     */
    private class FakeWalletSettingsRepository : WalletSettingsRepository {
        val removedWallets = mutableListOf<String>()
        private val walletsState = MutableStateFlow<List<WalletConnection>>(emptyList())
        private val activeWalletState = MutableStateFlow<WalletConnection?>(null)

        override val wallets: Flow<List<WalletConnection>> = walletsState
        override val walletConnection: Flow<WalletConnection?> = activeWalletState

        override suspend fun getWalletConnection(): WalletConnection? = activeWalletState.value

        override suspend fun getWallets(): List<WalletConnection> = walletsState.value

        override suspend fun saveWalletConnection(connection: WalletConnection, activate: Boolean) {
            walletsState.value = walletsState.value + connection
            if (activate) activeWalletState.value = connection
        }

        override suspend fun setActiveWallet(walletPublicKey: String) {
            activeWalletState.value = walletsState.value.find { it.walletPublicKey == walletPublicKey }
        }

        override suspend fun removeWallet(walletPublicKey: String) {
            removedWallets.add(walletPublicKey)
            walletsState.value = walletsState.value.filterNot { it.walletPublicKey == walletPublicKey }
            if (activeWalletState.value?.walletPublicKey == walletPublicKey) {
                activeWalletState.value = walletsState.value.firstOrNull()
            }
        }

        override suspend fun clearWalletConnection() {
            walletsState.value = emptyList()
            activeWalletState.value = null
        }
    }

    companion object {
        private const val TEST_WALLET_ID = "blink-test-wallet-123"
        private const val TEST_API_KEY = "blink_test_api_key"
        private const val TEST_BLINK_DEFAULT_WALLET_ID = "wallet-123"

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
                                "status": "SUCCESS",
                                "direction": "SEND"
                            }]
                        }]
                    }
                }
            }
        }"""
    }
}
