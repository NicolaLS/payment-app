package xyz.lilsus.flint.application.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import xyz.lilsus.raylsuite.core.model.Satoshi

class SparkPaymentDraftRegistryTest {
    @Test
    fun amountDraftBecomesPreparedDraftWithoutLeavingFingerprintAliasesBehind() {
        val registry = SparkPaymentDraftRegistry()
        val requestFingerprint = InvoiceFingerprint.lnurl("https://example.com/pay")
        val resolvedFingerprint = InvoiceFingerprint.bolt11("resolved-hash")
        val amountHandle = PaymentAmountHandle("shared-handle")
        val admission = admission(requestFingerprint)
        val amountProjection =
            AmountRequiredPayment(
                handle = amountHandle,
                method = PaymentMethod.BOLT11,
                expiresAtEpochSeconds = 200L
            )
        registry.registerAmount(
            amountHandle,
            AmountDraft(admission, amountProjection, PaymentOrigin.DEEP_LINK)
        )

        assertEquals(
            amountProjection,
            assertIs<PreparePaymentResult.AmountRequired>(
                registry.reusable(requestFingerprint, nowEpochSeconds = 100L)
            ).payment
        )
        assertNotNull(registry.consumeAmount(amountHandle))

        val draftHandle = PaymentDraftHandle(amountHandle.value)
        val preparedProjection = preparedPayment(draftHandle)
        registry.register(
            draftHandle,
            verifiedDraft(
                admission.copy(fingerprint = resolvedFingerprint),
                preparedProjection,
                setOf(requestFingerprint, resolvedFingerprint)
            )
        )

        assertEquals(
            preparedProjection,
            assertIs<PreparePaymentResult.Ready>(
                registry.reusable(requestFingerprint, nowEpochSeconds = 100L)
            ).payment
        )
        assertEquals(
            preparedProjection,
            assertIs<PreparePaymentResult.Ready>(
                registry.reusable(resolvedFingerprint, nowEpochSeconds = 100L)
            ).payment
        )

        assertNotNull(registry.consume(draftHandle))
        assertNull(registry.reusable(requestFingerprint, nowEpochSeconds = 100L))
        assertNull(registry.reusable(resolvedFingerprint, nowEpochSeconds = 100L))
    }

    @Test
    fun expiredDraftRemovesEveryFingerprintAlias() {
        val registry = SparkPaymentDraftRegistry()
        val originalFingerprint = InvoiceFingerprint.lnurl("https://example.com/expired")
        val resolvedFingerprint = InvoiceFingerprint.bolt11("expired-hash")
        val handle = PaymentDraftHandle("expired")
        registry.register(
            handle,
            verifiedDraft(
                admission(resolvedFingerprint, expiresAtEpochSeconds = 100L),
                preparedPayment(handle),
                setOf(originalFingerprint, resolvedFingerprint)
            )
        )

        assertNull(registry.reusable(originalFingerprint, nowEpochSeconds = 100L))
        assertNull(registry.draft(handle))
        assertNull(registry.reusable(resolvedFingerprint, nowEpochSeconds = 100L))
    }

    private fun admission(fingerprint: InvoiceFingerprint, expiresAtEpochSeconds: Long = 200L) = Admission.Accepted(
        invoice = "invoice",
        fingerprint = fingerprint,
        method = PaymentMethod.BOLT11,
        amountSats = Satoshi.positive(21),
        expiresAtEpochSeconds = expiresAtEpochSeconds
    )

    private fun preparedPayment(handle: PaymentDraftHandle) = PreparedPayment(
        handle = handle,
        method = PaymentMethod.BOLT11,
        amountSats = Satoshi.positive(21),
        feeSats = Satoshi.nonNegative(1),
        expiresAtEpochSeconds = 200L
    )

    private fun verifiedDraft(admission: Admission.Accepted, projection: PreparedPayment, fingerprints: Set<InvoiceFingerprint>) =
        VerifiedDraft(
            admission = admission,
            prepared =
                SdkPreparedPayment(
                    payload = TestPreparedPayload,
                    method = PaymentMethod.BOLT11,
                    amountSats = projection.amountSats,
                    feeSats = projection.feeSats,
                    tokenIdentifier = null,
                    hasConversion = false
                ),
            projection = projection,
            origin = PaymentOrigin.DEEP_LINK,
            fingerprints = fingerprints
        )

    private data object TestPreparedPayload : PreparedSdkPayload
}
