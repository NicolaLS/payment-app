package xyz.lilsus.blip.feature.payment

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.Feature
import fr.acinq.lightning.FeatureSupport
import fr.acinq.lightning.Features
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toByteVector32
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Job
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.payment.LnurlPayClient
import xyz.lilsus.raylsuite.core.payment.LnurlPayMetadata
import xyz.lilsus.raylsuite.core.payment.LnurlPayParams
import xyz.lilsus.raylsuite.core.payment.LnurlResult
import xyz.lilsus.raylsuite.core.payment.lnurlDynamicPaymentSourceKey
import xyz.lilsus.raylsuite.feature.paymentcurrency.CurrencyState
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountKey

class PaymentSessionStateTest {
    @Test
    fun resetClearsTheCompleteTransientSessionAndPreservesAppLivedObjects() {
        val appLivedState =
            mutableMapOf(
                "currency" to "EUR",
                "contacts" to "alice@example.com",
                "confirmation" to "always"
            )
        val preparation = PaymentPreparation(UnusedLnurlPayClient)
        val state = PaymentSessionState(preparation)
        val session = lnurlSession()
        val paymentJob = Job()

        preparation.manualAmount.handleKeyPress(ManualAmountKey.Digit(5))
        preparation.manualEntryContext =
            ManualEntryContext.Lnurl(
                session,
                CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE)
            )
        preparation.pendingLnurlReview = PendingLnurlReview(session, 5_000L, true)
        preparation.pendingPayment =
            PendingPayment(
                invoice(),
                null,
                PendingOrigin.Invoice,
                null,
                null,
                null
            )
        state.pendingRetry =
            PendingRetryChoice(
                "payment-id",
                PendingRetryContinuation.Lnurl(
                    "https://pay.example/request",
                    lnurlDynamicPaymentSourceKey("https://pay.example/request"),
                    PaymentRequestSource.Camera
                )
            )
        state.lastPaymentResult = CompletedPayment(5_000L, 1_000L, false, false, null)
        state.knownTransactionIds += "known"
        state.newTransactionIds += "new"
        state.paymentJobs["payment-id"] = paymentJob
        state.paymentAdmissionInProgress = true
        state.newSessionTransactionCount.value = 1
        state.transactionDetailNavigationTarget.value = "payment-id"
        state.uiState.value = PaymentUiState.Loading()

        state.reset(defaultCurrencyState())

        assertFalse(state.paymentAdmissionInProgress)
        assertTrue(paymentJob.isCancelled)
        assertTrue(state.paymentJobs.isEmpty())
        assertNull(preparation.manualEntryContext)
        assertNull(preparation.pendingPayment)
        assertNull(preparation.pendingLnurlReview)
        assertEquals("0", preparation.manualAmount.current().rawWhole)
        assertNull(state.pendingRetry)
        assertNull(state.lastPaymentResult)
        assertTrue(state.knownTransactionIds.isEmpty())
        assertTrue(state.newTransactionIds.isEmpty())
        assertEquals(0, state.newSessionTransactionCount.value)
        assertNull(state.transactionDetailNavigationTarget.value)
        assertEquals(PaymentUiState.Active, state.uiState.value)
        assertEquals("EUR", appLivedState["currency"])
        assertEquals("alice@example.com", appLivedState["contacts"])
        assertEquals("always", appLivedState["confirmation"])
    }

    private fun defaultCurrencyState() = CurrencyState(CurrencyCatalog.infoFor(CurrencyCatalog.DEFAULT_CODE), null)

    private fun lnurlSession() = LnurlSession(
        params =
            LnurlPayParams(
                callback = "https://pay.example/callback",
                minSendable = 1_000L,
                maxSendable = 10_000L,
                metadataRaw = "[]",
                metadata = LnurlPayMetadata(null, null, null, null, null, null, null),
                commentAllowed = null,
                domain = "pay.example"
            ),
        display = null,
        sourceKey = null,
        paymentSource = PaymentRequestSource.Camera,
        contactContext = null,
        comment = null,
        replacesDynamicGuardId = null
    )

    private fun invoice(): Bolt11Invoice = Bolt11Invoice.create(
        chain = Chain.Mainnet,
        amount = 5_000L.msat,
        paymentHash = Crypto.sha256("payment".encodeToByteArray()).toByteVector32(),
        privateKey = PrivateKey(ByteArray(32) { 1 }),
        description = Either.Left("test"),
        minFinalCltvExpiryDelta = Bolt11Invoice.DEFAULT_MIN_FINAL_EXPIRY_DELTA,
        features =
            Features(
                Feature.VariableLengthOnion to FeatureSupport.Optional,
                Feature.PaymentSecret to FeatureSupport.Optional
            ),
        timestampSeconds = 1L
    )
}

private object UnusedLnurlPayClient : LnurlPayClient {
    override suspend fun fetchPayParams(endpoint: String): LnurlResult<LnurlPayParams> = error("not used")

    override suspend fun fetchPayParams(address: LightningAddress): LnurlResult<LnurlPayParams> = error("not used")

    override suspend fun requestInvoice(callback: String, amountMsats: Long, comment: String?): LnurlResult<String> = error("not used")
}
