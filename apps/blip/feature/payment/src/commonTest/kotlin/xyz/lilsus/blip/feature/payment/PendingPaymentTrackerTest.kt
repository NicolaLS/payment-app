package xyz.lilsus.blip.feature.payment

import com.russhwolf.settings.MapSettings
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import xyz.lilsus.blip.integration.blink.BlinkFundingWallet
import xyz.lilsus.blip.integration.blink.BlinkWalletCurrency
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentCurrencyManager

class PendingPaymentTrackerTest {
    @Test
    fun fastSuccessIsPublishedToSessionPayments() = runTest {
        val tracker = tracker()
        val id =
            tracker.register(
                invoice(),
                AMOUNT_MSATS,
                null,
                TEST_FUNDING_WALLET,
                null,
                PendingOrigin.Invoice
            )

        tracker.markSuccess(id, AMOUNT_MSATS, feeMsats = 0)

        assertEquals(listOf(id), tracker.displayItems.value.map { it.id })
        assertEquals(PendingStatus.Success, tracker.displayItems.value.single().status)
    }

    @Test
    fun fastFailureIsPublishedToSessionPayments() = runTest {
        val tracker = tracker()
        val id =
            tracker.register(
                invoice(),
                AMOUNT_MSATS,
                null,
                TEST_FUNDING_WALLET,
                null,
                PendingOrigin.Invoice
            )

        tracker.markFailure(id, PaymentUiError.Unexpected("rejected"))

        assertEquals(listOf(id), tracker.displayItems.value.map { it.id })
        assertEquals(PendingStatus.Failure, tracker.displayItems.value.single().status)
    }

    @Test
    fun sessionRetainsAllUnresolvedAndOnlyTenResolvedPayments() = runTest {
        var now = 0L
        val tracker = tracker(clock = { ++now })
        val unresolvedId =
            tracker.register(
                invoice(),
                AMOUNT_MSATS,
                null,
                TEST_FUNDING_WALLET,
                null,
                PendingOrigin.Invoice
            )
        val oldestResolvedId =
            tracker.register(
                invoice("oldest"),
                AMOUNT_MSATS,
                null,
                TEST_FUNDING_WALLET,
                null,
                PendingOrigin.Invoice
            )
        tracker.markSuccess(oldestResolvedId, AMOUNT_MSATS, feeMsats = 0)

        repeat(12) {
            val id =
                tracker.register(
                    invoice("payment-$it"),
                    AMOUNT_MSATS,
                    null,
                    TEST_FUNDING_WALLET,
                    null,
                    PendingOrigin.Invoice
                )
            tracker.markSuccess(id, AMOUNT_MSATS, feeMsats = 0)
        }

        assertNotNull(tracker.get(unresolvedId))
        assertNotNull(tracker.get(oldestResolvedId))
        assertEquals(10, tracker.displayItems.value.size)
        assertEquals(
            10,
            tracker.displayItems.value.count { it.status == PendingStatus.Success }
        )

        tracker.focus(oldestResolvedId)

        assertTrue(tracker.displayItems.value.any { it.id == oldestResolvedId })
        assertEquals(10, tracker.displayItems.value.size)
    }

    @Test
    fun completedInvoiceRemainsKnownAndDynamicGuardRequiresExplicitReplacement() = runTest {
        val settings = MapSettings()
        val tracker = tracker(settings = settings)
        val sourceKey = DynamicPaymentSourceKey("lnurl:https://pay.example/request")
        val firstId =
            tracker.register(
                invoice("first"),
                AMOUNT_MSATS,
                null,
                TEST_FUNDING_WALLET,
                null,
                PendingOrigin.LnurlFixed,
                dynamicSourceKey = sourceKey
            )
        tracker.markSuccess(firstId, AMOUNT_MSATS, feeMsats = 0)

        assertEquals(firstId, tracker.findLatestByPaymentHash(requireNotNull(tracker.get(firstId)).paymentHashHex)?.id)
        assertEquals(firstId, tracker.findGuardingByDynamicSourceKey(sourceKey)?.id)

        val replacementId =
            tracker.register(
                invoice("replacement"),
                AMOUNT_MSATS,
                null,
                TEST_FUNDING_WALLET,
                null,
                PendingOrigin.LnurlFixed,
                dynamicSourceKey = sourceKey,
                replacesDynamicGuardId = firstId
            )

        assertEquals(false, tracker.get(firstId)?.guardsDynamicSource)
        assertEquals(replacementId, tracker.findGuardingByDynamicSourceKey(sourceKey)?.id)

        tracker.markFailure(replacementId, PaymentUiError.Unexpected("rejected"))
        tracker.close()

        val restored = tracker(settings = settings)

        assertNull(restored.findGuardingByDynamicSourceKey(sourceKey))

        restored.markSending(replacementId)

        assertEquals(replacementId, restored.findGuardingByDynamicSourceKey(sourceKey)?.id)
    }

    @Test
    fun interruptedAttemptAndCompletedGuardSurviveProcessRestart() = runTest {
        val settings = MapSettings()
        val sourceKey = DynamicPaymentSourceKey("lnurl:https://pay.example/restart")
        val original = tracker(settings = settings)
        val id =
            original.register(
                invoice("restart"),
                AMOUNT_MSATS,
                null,
                TEST_FUNDING_WALLET,
                null,
                PendingOrigin.LnurlFixed,
                dynamicSourceKey = sourceKey
            )
        val paymentHash = requireNotNull(original.get(id)).paymentHashHex
        original.close()

        val restored = tracker(settings = settings)

        assertEquals(PendingStatus.StatusUnknown, restored.get(id)?.status)
        assertEquals(TEST_FUNDING_WALLET, restored.get(id)?.fundingWallet)
        assertEquals(id, restored.findLatestByPaymentHash(paymentHash)?.id)
        assertEquals(id, restored.findGuardingByDynamicSourceKey(sourceKey)?.id)

        restored.markSuccess(
            id = id,
            paidMsats = AMOUNT_MSATS,
            feeMsats = 1,
            preimage = "must-not-be-persisted"
        )
        restored.close()

        val completed = tracker(settings = settings)

        assertEquals(PendingStatus.Success, completed.get(id)?.status)
        assertNull(completed.get(id)?.preimage)
        assertEquals(id, completed.findGuardingByDynamicSourceKey(sourceKey)?.id)
    }

    @Test
    fun usdFundingPayloadSurvivesProcessRestart() = runTest {
        val settings = MapSettings()
        val original = tracker(settings = settings)
        val id =
            original.register(
                invoice("usd"),
                AMOUNT_MSATS,
                AMOUNT_MSATS,
                TEST_USD_FUNDING_WALLET,
                25L,
                PendingOrigin.ManualEntry
            )
        original.close()

        val restored = tracker(settings = settings).get(id)

        assertEquals(TEST_USD_FUNDING_WALLET, restored?.fundingWallet)
        assertEquals(25L, restored?.fundingAmountCents)
    }

    @Test
    fun resetSessionDropsAllPayments() = runTest {
        val settings = MapSettings()
        val tracker = tracker(settings = settings)
        val id =
            tracker.register(
                invoice(),
                AMOUNT_MSATS,
                null,
                TEST_FUNDING_WALLET,
                null,
                PendingOrigin.Invoice
            )
        tracker.markSuccess(id, AMOUNT_MSATS, feeMsats = 0)

        tracker.resetSession()

        assertTrue(tracker.displayItems.value.isEmpty())
        assertEquals(null, tracker.get(id))
        assertTrue(tracker(settings = settings).displayItems.value.isEmpty())
    }

    private fun TestScope.tracker(clock: () -> Long = { 1L }, settings: MapSettings = MapSettings()): PendingPaymentTracker {
        val currencyManager =
            PaymentCurrencyManager(
                bitcoinPriceProvider = BitcoinPriceProvider { null },
                scope = this
            )
        return PendingPaymentTracker(
            currencyManager = currencyManager,
            scope = this,
            showEstimatedFeeHint = false,
            store = PendingPaymentStore(settings),
            clock = clock
        )
    }

    private fun invoice(seed: String = "payment"): Bolt11Invoice = Bolt11Invoice.create(
        chain = Chain.Mainnet,
        amount = AMOUNT_MSATS.msat,
        paymentHash = Crypto.sha256(seed.encodeToByteArray()).toByteVector32(),
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
        val TEST_FUNDING_WALLET =
            BlinkFundingWallet("wallet-btc", BlinkWalletCurrency.BTC)
        val TEST_USD_FUNDING_WALLET =
            BlinkFundingWallet("wallet-usd", BlinkWalletCurrency.USD)
    }
}
