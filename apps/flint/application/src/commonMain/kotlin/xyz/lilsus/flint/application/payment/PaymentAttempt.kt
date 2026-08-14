package xyz.lilsus.flint.application.payment

import kotlin.time.Clock
import okio.ByteString.Companion.toByteString
import xyz.lilsus.raylsuite.core.model.Satoshi

enum class PaymentMethod {
    BOLT11,
    SPARK_INVOICE
}

enum class PaymentOrigin {
    DETECTED_CONTENT,
    DEEP_LINK,
    MANUAL_RECOVERY,
    CONTRACT_TEST
}

enum class PaymentLinkPhase {
    CONFIRMED,
    SUBMISSION_STARTED,
    SDK_PAYMENT_LINKED
}

class InvoiceFingerprint private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is InvoiceFingerprint && value == other.value
    override fun hashCode(): Int = value.hashCode()
    override fun toString(): String = "InvoiceFingerprint(<redacted>)"

    companion object {
        fun bolt11(paymentHash: String): InvoiceFingerprint =
            InvoiceFingerprint("bolt11:${paymentHash.trim().lowercase()}")

        fun spark(invoice: String): InvoiceFingerprint = InvoiceFingerprint(
            "spark:${invoice.trim().lowercase().encodeToByteArray().toByteString().sha256().hex()}"
        )

        fun lnurl(destination: String): InvoiceFingerprint = InvoiceFingerprint(
            "lnurl:${destination.trim().encodeToByteArray().toByteString().sha256().hex()}"
        )

        fun persisted(value: String): InvoiceFingerprint = InvoiceFingerprint(value)
    }
}

data class PaymentAttempt(
    val attemptId: String,
    val fingerprint: InvoiceFingerprint,
    val method: PaymentMethod,
    val amountSats: Satoshi,
    val feeSats: Satoshi,
    val origin: PaymentOrigin,
    val createdAtEpochSeconds: Long,
    val updatedAtEpochSeconds: Long,
    val linkPhase: PaymentLinkPhase,
    val breezPaymentId: String?,
    val fiatQuote: FiatAmountQuote? = null
)

sealed interface CreateAttemptResult {
    data class Created(val attempt: PaymentAttempt) : CreateAttemptResult
    data class Existing(val attempt: PaymentAttempt) : CreateAttemptResult
    data object CapacityReached : CreateAttemptResult
    data object Failed : CreateAttemptResult
}

interface PaymentAttemptRepository {
    fun createConfirmed(
        attemptId: String,
        fingerprint: InvoiceFingerprint,
        method: PaymentMethod,
        amountSats: Satoshi,
        feeSats: Satoshi,
        origin: PaymentOrigin,
        nowEpochSeconds: Long,
        fiatQuote: FiatAmountQuote? = null
    ): CreateAttemptResult

    fun findById(attemptId: String): PaymentAttempt?
    fun findByFingerprint(fingerprint: InvoiceFingerprint): PaymentAttempt?
    fun unresolved(): List<PaymentAttempt>
    fun linked(): List<PaymentAttempt>
    fun all(): List<PaymentAttempt>
    fun markSubmissionStarted(attemptId: String, nowEpochSeconds: Long): Boolean
    fun linkPayment(attemptId: String, breezPaymentId: String, nowEpochSeconds: Long): Boolean
    fun clear()
}

fun currentEpochSeconds(): Long = Clock.System.now().epochSeconds
