package xyz.lilsus.papp.domain.usecases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.PaymentProvider

/**
 * Tests for LookupPaymentUseCase.
 * Verifies that wallet context is properly passed through to the payment provider.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LookupPaymentUseCaseTest {

    @Test
    fun invokePasses_walletContextToProvider() = runTest {
        val provider = RecordingPaymentProvider()
        val useCase = LookupPaymentUseCase(provider)

        val expectedHash = "test-payment-hash"
        val expectedUri = "nostr+walletconnect://test-wallet"
        val expectedType = WalletType.NWC

        useCase(expectedHash, expectedUri, expectedType)

        assertEquals(expectedHash, provider.lastPaymentHash)
        assertEquals(expectedUri, provider.lastWalletUri)
        assertEquals(expectedType, provider.lastWalletType)
    }

    @Test
    fun invokePassesNullWalletContext_whenNotProvided() = runTest {
        val provider = RecordingPaymentProvider()
        val useCase = LookupPaymentUseCase(provider)

        useCase("test-hash")

        assertEquals("test-hash", provider.lastPaymentHash)
        assertEquals(null, provider.lastWalletUri)
        assertEquals(null, provider.lastWalletType)
    }

    @Test
    fun invokeReturnsResultFromProvider() = runTest {
        val expectedResult = PaymentLookupResult.Settled(PaidInvoice(preimage = "test", feesPaidMsats = 100L))
        val provider = RecordingPaymentProvider(result = expectedResult)
        val useCase = LookupPaymentUseCase(provider)

        val result = useCase("test-hash")

        assertEquals(expectedResult, result)
    }

    @Test
    fun concurrentLookupsOnDifferentWalletsAreIndependent() = runTest {
        val provider = ConcurrentRecordingPaymentProvider()
        val useCase = LookupPaymentUseCase(provider)

        val nwcWalletUri = "nostr+walletconnect://wallet-1"
        val blinkWalletUri = "blink-wallet-2"

        // Launch two concurrent lookups on different wallets
        val lookup1 = async {
            useCase("hash-1", nwcWalletUri, WalletType.NWC)
        }
        val lookup2 = async {
            useCase("hash-2", blinkWalletUri, WalletType.BLINK)
        }

        // Wait for both
        lookup1.await()
        lookup2.await()

        // Verify both lookups were made with their respective wallet contexts
        assertEquals(2, provider.lookupCalls.size)

        val call1 = provider.lookupCalls.find { it.paymentHash == "hash-1" }
        val call2 = provider.lookupCalls.find { it.paymentHash == "hash-2" }

        assertTrue(call1 != null, "Expected lookup call for hash-1")
        assertEquals(nwcWalletUri, call1!!.walletUri)
        assertEquals(WalletType.NWC, call1.walletType)

        assertTrue(call2 != null, "Expected lookup call for hash-2")
        assertEquals(blinkWalletUri, call2!!.walletUri)
        assertEquals(WalletType.BLINK, call2.walletType)
    }

    private class RecordingPaymentProvider(private val result: PaymentLookupResult = PaymentLookupResult.NotFound) : PaymentProvider {
        var lastPaymentHash: String? = null
        var lastWalletUri: String? = null
        var lastWalletType: WalletType? = null

        override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
            error("Not implemented")
        }

        override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?): PayInvoiceRequest {
            error("Not implemented")
        }

        override suspend fun lookupPayment(paymentHash: String, walletUri: String?, walletType: WalletType?): PaymentLookupResult {
            lastPaymentHash = paymentHash
            lastWalletUri = walletUri
            lastWalletType = walletType
            return result
        }
    }

    /** Thread-safe provider that records all lookup calls for concurrent testing. */
    private class ConcurrentRecordingPaymentProvider : PaymentProvider {
        data class LookupCall(val paymentHash: String, val walletUri: String?, val walletType: WalletType?)

        private val _lookupCalls = mutableListOf<LookupCall>()
        val lookupCalls: List<LookupCall> get() = _lookupCalls.toList()

        override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
            error("Not implemented")
        }

        override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?): PayInvoiceRequest {
            error("Not implemented")
        }

        override suspend fun lookupPayment(paymentHash: String, walletUri: String?, walletType: WalletType?): PaymentLookupResult {
            _lookupCalls.add(LookupCall(paymentHash, walletUri, walletType))
            return PaymentLookupResult.NotFound
        }
    }
}
