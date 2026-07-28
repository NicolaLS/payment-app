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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider

class PendingPaymentTrackerTest {
    @Test
    fun fastSuccessIsPublishedToSessionPayments() = runTest {
        val tracker = tracker()
        val id = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)

        tracker.markSuccess(id, AMOUNT_MSATS, feeMsats = 0)

        assertEquals(listOf(id), tracker.displayItems.value.map { it.id })
        assertEquals(PendingStatus.Success, tracker.displayItems.value.single().status)
    }

    @Test
    fun fastFailureIsPublishedToSessionPayments() = runTest {
        val tracker = tracker()
        val id = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)

        tracker.markFailure(id, PaymentUiError.Unexpected("rejected"))

        assertEquals(listOf(id), tracker.displayItems.value.map { it.id })
        assertEquals(PendingStatus.Failure, tracker.displayItems.value.single().status)
    }

    @Test
    fun sessionRetainsAllUnresolvedAndOnlyTenResolvedPayments() = runTest {
        var now = 0L
        val tracker = tracker(clock = { ++now })
        val unresolvedId =
            tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)

        repeat(12) {
            val id = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)
            tracker.markSuccess(id, AMOUNT_MSATS, feeMsats = 0)
        }

        val items = tracker.displayItems.value
        assertNotNull(tracker.get(unresolvedId))
        assertEquals(10, items.size)
        assertEquals(10, items.count { it.status == PendingStatus.Success })
    }

    @Test
    fun resetSessionDropsAllPayments() = runTest {
        val tracker = tracker()
        val id = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)
        tracker.markSuccess(id, AMOUNT_MSATS, feeMsats = 0)

        tracker.resetSession()

        assertTrue(tracker.displayItems.value.isEmpty())
        assertEquals(null, tracker.get(id))
    }

    private fun TestScope.tracker(clock: () -> Long = { 1L }): PendingPaymentTracker {
        val currencyManager =
            PaymentCurrencyManager(
                bitcoinPriceProvider = BitcoinPriceProvider { null },
                scope = this
            )
        return PendingPaymentTracker(
            currencyManager = currencyManager,
            scope = this,
            showEstimatedFeeHint = false,
            clock = clock
        )
    }

    private fun invoice(): Bolt11Invoice = Bolt11Invoice.create(
        chain = Chain.Mainnet,
        amount = AMOUNT_MSATS.msat,
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

    private companion object {
        const val AMOUNT_MSATS = 100_000L
    }
}
