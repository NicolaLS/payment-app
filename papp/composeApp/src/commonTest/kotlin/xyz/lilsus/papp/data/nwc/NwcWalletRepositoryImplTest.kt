package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcSession
import io.github.nostr.nwc.model.NwcError
import io.github.nostr.nwc.model.NwcFailure
import io.github.nostr.nwc.model.NwcResult
import io.github.nostr.nwc.model.PayInvoiceResult
import io.github.nostr.nwc.testing.FakeNwcClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NwcWalletRepositoryImplTest {

    @Test
    fun payInvoiceReturnsPaidInvoiceOnSuccess() = runTest {
        val fakeClient = FakeNwcClient()
        fakeClient.defaultPayInvoiceResult = NwcResult.Success(
            PayInvoiceResult(
                preimage = "preimage",
                feesPaid = null,
            )
        )
        val context = createRepository(fakeClient)

        val paid = context.repository.payInvoice(invoice = SAMPLE_INVOICE, amountMsats = null)

        assertEquals("preimage", paid.preimage)
        assertEquals(null, paid.feesPaidMsats)

        context.close()
    }

    @Test
    fun payInvoiceMapsWalletFailuresToPaymentRejected() = runTest {
        val fakeClient = FakeNwcClient()
        fakeClient.defaultPayInvoiceResult = NwcResult.Failure(
            NwcFailure.Wallet(NwcError(code = "401", message = "rejected"))
        )
        val context = createRepository(fakeClient)

        val error = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(SAMPLE_INVOICE, null)
        }

        assertEquals(AppError.PaymentRejected(code = "401", message = "rejected"), error.error)

        context.close()
    }

    @Test
    fun payInvoiceMapsNetworkFailuresToNetworkUnavailable() = runTest {
        val fakeClient = FakeNwcClient()
        fakeClient.defaultPayInvoiceResult = NwcResult.Failure(
            NwcFailure.Network(message = "offline")
        )
        val context = createRepository(fakeClient)

        val error = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(SAMPLE_INVOICE, null)
        }

        assertEquals(AppError.NetworkUnavailable, error.error)

        context.close()
    }

    @Test
    fun payInvoiceMapsTimeoutFailuresToTimeoutError() = runTest {
        val fakeClient = FakeNwcClient()
        fakeClient.defaultPayInvoiceResult = NwcResult.Failure(
            NwcFailure.Timeout(message = "slow wallet")
        )
        val context = createRepository(fakeClient)

        val error = assertFailsWith<AppErrorException> {
            context.repository.payInvoice(SAMPLE_INVOICE, null)
        }

        assertEquals(AppError.Timeout, error.error)

        context.close()
    }

    private fun createRepository(
        client: FakeNwcClient,
        connection: WalletConnection = WalletConnection(
            uri = SAMPLE_URI,
            walletPublicKey = SAMPLE_PUBKEY,
            relayUrl = "wss://relay.example",
            lud16 = null,
            alias = null,
        ),
    ): RepositoryContext {
        val session = NwcSession.create(uri = connection.uri)
        val handle = NwcClientHandle(
            uri = connection.uri,
            session = session,
            client = client,
            release = {
                session.close()
            },
        )
        val factory = FakeClientFactory(handle)
        val testScope = TestScope()
        val repository = NwcWalletRepositoryImpl(
            walletSettingsRepository = StubWalletSettingsRepository(connection),
            clientFactory = factory,
            scope = testScope,
        )
        return RepositoryContext(repository, factory, handle, testScope)
    }

    private class FakeClientFactory(
        private val handle: NwcClientHandle,
    ) : NwcClientFactory {
        override suspend fun create(connection: WalletConnection): NwcClientHandle {
            require(connection.uri == handle.uri) {
                "Test expects URI ${handle.uri} but got ${connection.uri}"
            }
            return handle
        }
    }

    private class StubWalletSettingsRepository(
        private val connection: WalletConnection?,
    ) : WalletSettingsRepository {
        override val wallets: Flow<List<WalletConnection>> = flowOf(connection?.let { listOf(it) } ?: emptyList())
        override val walletConnection: Flow<WalletConnection?> = flowOf(connection)

        override suspend fun getWalletConnection(): WalletConnection? = connection

        override suspend fun saveWalletConnection(connection: WalletConnection, activate: Boolean) {
            throw UnsupportedOperationException()
        }

        override suspend fun setActiveWallet(walletPublicKey: String) {
            throw UnsupportedOperationException()
        }

        override suspend fun removeWallet(walletPublicKey: String) {
            throw UnsupportedOperationException()
        }

        override suspend fun getWallets(): List<WalletConnection> {
            throw UnsupportedOperationException()
        }

        override suspend fun clearWalletConnection() {
            throw UnsupportedOperationException()
        }
    }

    private data class RepositoryContext(
        val repository: NwcWalletRepositoryImpl,
        val factory: FakeClientFactory,
        val handle: NwcClientHandle,
        val testScope: TestScope,
    ) {
        suspend fun close() {
            testScope.cancel("Test completed")
            handle.release()
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
