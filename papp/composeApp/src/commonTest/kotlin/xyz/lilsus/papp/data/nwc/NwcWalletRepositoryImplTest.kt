package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcRequest
import io.github.nostr.nwc.NwcWalletContract
import io.github.nostr.nwc.model.BalanceResult
import io.github.nostr.nwc.model.GetInfoResult
import io.github.nostr.nwc.model.KeysendParams
import io.github.nostr.nwc.model.KeysendResult
import io.github.nostr.nwc.model.ListTransactionsParams
import io.github.nostr.nwc.model.LookupInvoiceParams
import io.github.nostr.nwc.model.MakeInvoiceParams
import io.github.nostr.nwc.model.NwcError
import io.github.nostr.nwc.model.NwcFailure
import io.github.nostr.nwc.model.NwcRequestState
import io.github.nostr.nwc.model.NwcResult
import io.github.nostr.nwc.model.PayInvoiceParams
import io.github.nostr.nwc.model.PayInvoiceResult
import io.github.nostr.nwc.model.Transaction
import io.github.nostr.nwc.model.WalletMetadata
import io.github.nostr.nwc.model.WalletNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class NwcWalletRepositoryImplTest {

    @Test
    fun payInvoiceReturnsPaidInvoiceOnSuccess() = runTest {
        val fakeWallet = FakeNwcWallet()
        fakeWallet.nextPayInvoiceResult = NwcRequestState.Success(
            PayInvoiceResult(
                preimage = "preimage",
                feesPaid = null
            )
        )
        val context = createRepository(fakeWallet)

        val paid = context.repository.payInvoice(invoice = SAMPLE_INVOICE, amountMsats = null)

        assertEquals("preimage", paid.preimage)
        assertEquals(null, paid.feesPaidMsats)

        context.close()
    }

    @Test
    fun payInvoiceMapsWalletFailuresToPaymentRejected() = runTest {
        val fakeWallet = FakeNwcWallet()
        fakeWallet.nextPayInvoiceResult = NwcRequestState.Failure(
            NwcFailure.Wallet(NwcError(code = "401", message = "rejected"))
        )
        val context = createRepository(fakeWallet)

        val error = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(SAMPLE_INVOICE, null)
        }

        assertEquals(AppError.PaymentRejected(code = "401", message = "rejected"), error.error)

        context.close()
    }

    @Test
    fun payInvoiceMapsNetworkFailuresToNetworkUnavailable() = runTest {
        val fakeWallet = FakeNwcWallet()
        fakeWallet.nextPayInvoiceResult = NwcRequestState.Failure(
            NwcFailure.Network(message = "offline")
        )
        val context = createRepository(fakeWallet)

        val error = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(SAMPLE_INVOICE, null)
        }

        assertEquals(AppError.NetworkUnavailable, error.error)

        context.close()
    }

    @Test
    fun payInvoiceMapsTimeoutFailuresToTimeoutError() = runTest {
        val fakeWallet = FakeNwcWallet()
        fakeWallet.nextPayInvoiceResult = NwcRequestState.Failure(
            NwcFailure.Timeout(message = "slow wallet")
        )
        val context = createRepository(fakeWallet)

        val error = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(SAMPLE_INVOICE, null)
        }

        assertEquals(AppError.Timeout, error.error)

        context.close()
    }

    private fun createRepository(
        wallet: FakeNwcWallet,
        connection: WalletConnection = WalletConnection(
            uri = SAMPLE_URI,
            walletPublicKey = SAMPLE_PUBKEY,
            relayUrl = "wss://relay.example",
            lud16 = null,
            alias = null
        )
    ): RepositoryContext {
        val factory = FakeNwcWalletFactory(wallet)
        val testScope = TestScope(UnconfinedTestDispatcher())
        val repository = NwcWalletRepositoryImpl(
            walletSettingsRepository = StubWalletSettingsRepository(connection),
            walletFactory = factory,
            scope = testScope
        )
        // Let init block coroutine start
        testScope.testScheduler.advanceUntilIdle()
        return RepositoryContext(repository, wallet, testScope)
    }

    /**
     * Fake [NwcWalletContract] for testing that allows configuring responses.
     */
    private class FakeNwcWallet : NwcWalletContract {
        override val uri: String = SAMPLE_URI

        var nextPayInvoiceResult: NwcRequestState<PayInvoiceResult> = NwcRequestState.Loading

        override val walletMetadata: StateFlow<WalletMetadata?> = MutableStateFlow(null)
        override val notifications: SharedFlow<WalletNotification> = MutableSharedFlow()

        override fun payInvoice(params: PayInvoiceParams): NwcRequest<PayInvoiceResult> = createFakeRequest(nextPayInvoiceResult)

        override fun payKeysend(params: KeysendParams): NwcRequest<KeysendResult> =
            throw UnsupportedOperationException("Not implemented in test")

        override fun getBalance(): NwcRequest<BalanceResult> = throw UnsupportedOperationException("Not implemented in test")

        override fun getInfo(): NwcRequest<GetInfoResult> = throw UnsupportedOperationException("Not implemented in test")

        override fun makeInvoice(params: MakeInvoiceParams): NwcRequest<Transaction> =
            throw UnsupportedOperationException("Not implemented in test")

        override fun lookupInvoice(params: LookupInvoiceParams): NwcRequest<Transaction> =
            throw UnsupportedOperationException("Not implemented in test")

        override fun listTransactions(params: ListTransactionsParams): NwcRequest<List<Transaction>> =
            throw UnsupportedOperationException("Not implemented in test")

        override suspend fun payInvoiceAndWait(params: PayInvoiceParams, timeout: Duration): NwcResult<PayInvoiceResult> =
            throw UnsupportedOperationException("Not implemented in test")

        override suspend fun getBalanceAndWait(timeout: Duration): NwcResult<BalanceResult> =
            throw UnsupportedOperationException("Not implemented in test")

        override suspend fun getInfoAndWait(timeout: Duration): NwcResult<GetInfoResult> =
            throw UnsupportedOperationException("Not implemented in test")

        override suspend fun makeInvoiceAndWait(params: MakeInvoiceParams, timeout: Duration): NwcResult<Transaction> =
            throw UnsupportedOperationException("Not implemented in test")

        override suspend fun close() {
            // No-op
        }

        private fun <T> createFakeRequest(result: NwcRequestState<T>): NwcRequest<T> {
            val stateFlow = MutableStateFlow(result)
            return NwcRequest.createForTest(
                state = stateFlow,
                requestId = "test-request",
                job = Job()
            )
        }
    }

    private class FakeNwcWalletFactory(private val wallet: FakeNwcWallet) : NwcWalletFactory {
        override fun create(connection: WalletConnection): NwcWalletContract = wallet
    }

    private class StubWalletSettingsRepository(private val connection: WalletConnection?) : WalletSettingsRepository {
        private val _wallets = MutableStateFlow(connection?.let { listOf(it) } ?: emptyList())
        private val _walletConnection = MutableStateFlow(connection)

        override val wallets: Flow<List<WalletConnection>> = _wallets
        override val walletConnection: StateFlow<WalletConnection?> = _walletConnection

        override suspend fun getWalletConnection(): WalletConnection? = connection

        override suspend fun saveWalletConnection(connection: WalletConnection, activate: Boolean): Unit =
            throw UnsupportedOperationException()

        override suspend fun setActiveWallet(walletPublicKey: String): Unit = throw UnsupportedOperationException()

        override suspend fun removeWallet(walletPublicKey: String): Unit = throw UnsupportedOperationException()

        override suspend fun getWallets(): List<WalletConnection> = throw UnsupportedOperationException()

        override suspend fun clearWalletConnection(): Unit = throw UnsupportedOperationException()
    }

    private data class RepositoryContext(val repository: NwcWalletRepositoryImpl, val wallet: FakeNwcWallet, val testScope: TestScope) {
        suspend fun close() {
            testScope.cancel("Test completed")
            wallet.close()
        }
    }

    companion object {
        private const val SAMPLE_PUBKEY =
            "b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4"
        private const val SAMPLE_SECRET =
            "71a8c14c1407c113601079c4302dab36460f0ccd0ad506f1f2dc73b5100e4f3c"
        private const val SAMPLE_URI =
            "nostr+walletconnect://$SAMPLE_PUBKEY?relay=wss://relay.example&secret=$SAMPLE_SECRET"
        private const val SAMPLE_INVOICE = "lnbc1dummyinvoice"
    }
}
