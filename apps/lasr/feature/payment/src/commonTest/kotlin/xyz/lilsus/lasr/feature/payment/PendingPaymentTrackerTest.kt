package xyz.lilsus.lasr.feature.payment

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import xyz.lilsus.lasr.integration.nwc.NwcLookupOutcome
import xyz.lilsus.lasr.integration.nwc.NwcPayOutcome
import xyz.lilsus.lasr.integration.nwc.NwcSentPayment
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.core.payment.DynamicPaymentSourceKey
import xyz.lilsus.raylsuite.feature.paymentcurrency.PaymentCurrencyManager

@OptIn(ExperimentalCoroutinesApi::class)
class PendingPaymentTrackerTest {
    @Test
    fun fastSuccessAndFailureAreVisibleImmediately() = runTest {
        val tracker = tracker()
        val successId = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)
        val failureId = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)

        tracker.applyPayOutcome(successId, NwcPayOutcome.Settled("preimage", 10))
        tracker.applyPayOutcome(
            failureId,
            NwcPayOutcome.WalletRejected("DENIED", "rejected")
        )

        assertEquals(PendingStatus.Succeeded, tracker.get(successId)?.status)
        assertEquals(PendingStatus.Failed, tracker.get(failureId)?.status)
        assertEquals(2, tracker.displayItems.value.size)
    }

    @Test
    fun paymentBecomesVisibleAtFiveSecondsAndSettlesThroughLookup() = runTest {
        var lookupCount = 0
        val tracker =
            tracker { _, _ ->
                lookupCount++
                NwcLookupOutcome.Settled("preimage", 20)
            }
        val id = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)

        advanceTimeBy(4_999)
        runCurrent()
        assertTrue(tracker.displayItems.value.isEmpty())

        advanceTimeBy(1)
        runCurrent()

        assertEquals(1, lookupCount)
        assertEquals(PendingStatus.Succeeded, tracker.get(id)?.status)
    }

    @Test
    fun pendingNotFoundAndTransportFailuresKeepResolving() = runTest {
        val outcomes =
            ArrayDeque<NwcLookupOutcome>().apply {
                add(NwcLookupOutcome.Pending)
                add(NwcLookupOutcome.NotFound)
                add(NwcLookupOutcome.RetryableFailure("offline"))
                add(NwcLookupOutcome.Settled("preimage", 0))
            }
        val tracker = tracker { _, _ -> outcomes.removeFirst() }
        val id = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(PendingStatus.Resolving, tracker.get(id)?.status)

        advanceTimeBy(2_000)
        runCurrent()
        advanceTimeBy(4_000)
        runCurrent()
        advanceTimeBy(8_000)
        runCurrent()

        assertEquals(PendingStatus.Succeeded, tracker.get(id)?.status)
    }

    @Test
    fun backgroundPausesLookupAndForegroundResumesWithoutResending() = runTest {
        val foreground = MutableStateFlow(false)
        var lookupCount = 0
        val tracker =
            tracker(
                foreground = foreground,
                lookup = { _, _ ->
                    lookupCount++
                    NwcLookupOutcome.Settled("preimage", 0)
                }
            )
        val id = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, lookupCount)
        assertEquals(PendingStatus.Resolving, tracker.get(id)?.status)

        foreground.value = true
        runCurrent()

        assertEquals(1, lookupCount)
        assertEquals(PendingStatus.Succeeded, tracker.get(id)?.status)
    }

    @Test
    fun settledNotificationOverridesFailureAndCannotBeDowngraded() = runTest {
        val tracker = tracker()
        val id = tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)
        val paymentHash = requireNotNull(tracker.get(id)).paymentHashHex

        tracker.applyPayOutcome(id, NwcPayOutcome.WalletRejected("DENIED", "rejected"))
        tracker.applySentPayment(
            NwcSentPayment(
                paymentHash = paymentHash,
                invoice = null,
                preimage = "preimage",
                feesPaidMsats = 15
            )
        )
        tracker.applyPayOutcome(id, NwcPayOutcome.WalletRejected("LATE", "late rejection"))

        assertEquals(PendingStatus.Succeeded, tracker.get(id)?.status)
        assertEquals("preimage", tracker.get(id)?.preimage)
    }

    @Test
    fun completedDynamicGuardIsReplacedExplicitlyAndDefinitiveFailureReleasesIt() = runTest {
        val settings = MapSettings()
        val tracker = tracker(settings = settings)
        val sourceKey = DynamicPaymentSourceKey("lnurl:https://pay.example")
        val settledId =
            tracker.register(
                invoice("settled"),
                AMOUNT_MSATS,
                null,
                PendingOrigin.LnurlFixed,
                dynamicSourceKey = sourceKey
            )
        tracker.applyPayOutcome(settledId, NwcPayOutcome.Settled("preimage", 0))

        assertEquals(settledId, tracker.findGuardingByDynamicSourceKey(sourceKey)?.id)
        assertEquals(
            settledId,
            tracker.findLatestByPaymentHash(requireNotNull(tracker.get(settledId)).paymentHashHex)?.id
        )

        val replacementId =
            tracker.register(
                invoice("replacement"),
                AMOUNT_MSATS,
                null,
                PendingOrigin.LnurlFixed,
                dynamicSourceKey = sourceKey,
                replacesDynamicGuardId = settledId
            )

        assertEquals(false, tracker.get(settledId)?.guardsDynamicSource)
        assertEquals(replacementId, tracker.findGuardingByDynamicSourceKey(sourceKey)?.id)

        tracker.applyPayOutcome(
            replacementId,
            NwcPayOutcome.WalletRejected("DENIED", "rejected")
        )

        assertNull(tracker.findGuardingByDynamicSourceKey(sourceKey))

        val retried = tracker.retry(replacementId)

        assertEquals(replacementId, retried?.id)
        assertEquals(replacementId, tracker.findGuardingByDynamicSourceKey(sourceKey)?.id)

        tracker.applyPayOutcome(
            replacementId,
            NwcPayOutcome.WalletRejected("DENIED", "rejected")
        )
        tracker.close()

        val restored = tracker(settings = settings)

        assertNull(restored.get(replacementId))
        assertNull(restored.findGuardingByDynamicSourceKey(sourceKey))
        assertNull(restored.retry(replacementId))
    }

    @Test
    fun interruptedAttemptIsReconciledButCompletedResultDoesNotSurviveAnotherRestart() = runTest {
        val settings = MapSettings()
        val sourceKey = DynamicPaymentSourceKey("lnurl:https://pay.example/restart")
        val original = tracker(settings = settings)
        val id =
            original.register(
                invoice("restart"),
                AMOUNT_MSATS,
                null,
                PendingOrigin.LnurlFixed,
                dynamicSourceKey = sourceKey
            )
        val paymentHash = requireNotNull(original.get(id)).paymentHashHex
        original.close()
        var reconciledHash: String? = null

        val restored =
            tracker(
                settings = settings,
                lookup = { hash, _ ->
                    reconciledHash = hash
                    NwcLookupOutcome.Settled("must-not-be-persisted", 3)
                }
            )
        runCurrent()

        assertEquals(paymentHash, reconciledHash)
        assertEquals(PendingStatus.Succeeded, restored.get(id)?.status)
        assertEquals(id, restored.findGuardingByDynamicSourceKey(sourceKey)?.id)
        assertTrue(PendingPaymentStore(settings).load().isEmpty())
        // Simulate a resolved record written by the previous storage policy.
        PendingPaymentStore(settings).save(listOf(requireNotNull(restored.get(id))))
        restored.close()

        val completed = tracker(settings = settings)

        assertNull(completed.get(id))
        assertTrue(completed.displayItems.value.isEmpty())
        assertNull(completed.findGuardingByDynamicSourceKey(sourceKey))
    }

    @Test
    fun unknownRetryReusesRecord() = runTest {
        val tracker = tracker()

        val unknownId =
            tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)
        tracker.makeVisible(unknownId)
        tracker.applyPayOutcome(unknownId, NwcPayOutcome.Uncertain("uncertain"))
        runCurrent()
        assertEquals(PendingStatus.OutcomeUnknown, tracker.get(unknownId)?.status)

        val retried = tracker.retry(unknownId)
        assertEquals(unknownId, retried?.id)
        assertEquals(PendingStatus.Sending, tracker.get(unknownId)?.status)
    }

    @Test
    fun sessionRetainsAllUnresolvedAndOnlyTenResolvedAttempts() = runTest {
        var now = 0L
        val settings = MapSettings()
        val tracker = tracker(clock = { ++now }, settings = settings)
        val unresolvedId =
            tracker.register(invoice(), AMOUNT_MSATS, null, PendingOrigin.Invoice)
        tracker.makeVisible(unresolvedId)
        val oldestResolvedId =
            tracker.register(invoice("oldest"), AMOUNT_MSATS, null, PendingOrigin.Invoice)
        tracker.applyPayOutcome(oldestResolvedId, NwcPayOutcome.Settled(null, 0))

        repeat(12) {
            val id = tracker.register(invoice("payment-$it"), AMOUNT_MSATS, null, PendingOrigin.Invoice)
            tracker.applyPayOutcome(id, NwcPayOutcome.Settled(null, 0))
        }

        assertNotNull(tracker.get(unresolvedId))
        assertNotNull(tracker.get(oldestResolvedId))
        assertEquals(11, tracker.displayItems.value.size)
        assertEquals(10, tracker.displayItems.value.count { it.status == PendingStatus.Succeeded })

        tracker.focus(oldestResolvedId)

        assertTrue(tracker.displayItems.value.any { it.id == oldestResolvedId })
        assertEquals(11, tracker.displayItems.value.size)

        tracker.resetSession()
        assertTrue(tracker.displayItems.value.isEmpty())
        assertTrue(tracker(settings = settings).displayItems.value.isEmpty())
    }

    private fun TestScope.tracker(
        foreground: MutableStateFlow<Boolean> = MutableStateFlow(true),
        clock: () -> Long = { 1L },
        settings: MapSettings = MapSettings(),
        lookup: suspend (String, Long) -> NwcLookupOutcome = {
                _,
                _
            ->
            NwcLookupOutcome.PermanentlyUnavailable("unsupported")
        }
    ): PendingPaymentTracker {
        val currencyManager =
            PaymentCurrencyManager(
                bitcoinPriceProvider = BitcoinPriceProvider { null },
                scope = this
            )
        return PendingPaymentTracker(
            lookupInvoice = lookup,
            isInForeground = foreground,
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
    }
}
