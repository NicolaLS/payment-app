package xyz.lilsus.rayl.blip.domain

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice

data class PaymentDraft(
    val invoice: Bolt11Invoice,
    val originalRequest: String,
    val amount: MilliSatoshi,
    val memo: String?,
    val origin: PaymentOrigin,
    val requestKind: PaymentRequestKind,
    val rateSnapshot: ExchangeRateSnapshot? = null
) {
    val paymentHash: PaymentHash = PaymentHash(invoice.paymentHash)
    val fingerprint: String = when (requestKind) {
        PaymentRequestKind.FixedInvoice -> "bolt11:${paymentHash.hex}"
        PaymentRequestKind.DynamicRequest -> "dynamic:${paymentHash.hex}"
    }
}

enum class PaymentRequestKind {
    FixedInvoice,
    DynamicRequest
}

enum class PaymentOrigin {
    Scan,
    Paste,
    Manual,
    AppLink,
    Shortcut
}

enum class PaymentAttemptState {
    Created,
    Submitted,
    Pending,
    Settled,
    AlreadyPaid,
    Rejected,
    Unknown
}

data class PaymentAttempt(
    val id: AttemptId,
    val connectionId: ConnectionId,
    val request: String,
    val fingerprint: String,
    val paymentHash: PaymentHash,
    val amount: MilliSatoshi,
    val origin: PaymentOrigin,
    val state: PaymentAttemptState,
    val providerCorrelation: String?,
    val createdAtMillis: Long,
    val submittedAtMillis: Long?,
    val updatedAtMillis: Long,
    val feesPaid: MilliSatoshi?,
    val preimage: ByteVector32?,
    val failure: PaymentFailure?
)

sealed interface PaymentFailure {
    data object InvalidRequest : PaymentFailure
    data object ExpiredInvoice : PaymentFailure
    data object WrongNetwork : PaymentFailure
    data object MissingConnection : PaymentFailure
    data object AuthenticationRequired : PaymentFailure
    data object PermissionDenied : PaymentFailure
    data object InsufficientBalance : PaymentFailure
    data object RouteNotFound : PaymentFailure
    data object RateLimited : PaymentFailure
    data object NetworkUnavailable : PaymentFailure
    data object TimedOut : PaymentFailure
    data object DuplicateInvoice : PaymentFailure
    data class ProviderRejected(val code: String?) : PaymentFailure
    data class Unsupported(val kind: String) : PaymentFailure
    data object Unexpected : PaymentFailure
}

sealed interface SubmitPaymentOutcome {
    data class Settled(val feesPaid: MilliSatoshi?, val preimage: ByteVector32?) :
        SubmitPaymentOutcome

    data class AlreadyPaid(val preimage: ByteVector32?) : SubmitPaymentOutcome

    data object Pending : SubmitPaymentOutcome
    data class Rejected(val failure: PaymentFailure) : SubmitPaymentOutcome
    data object Unknown : SubmitPaymentOutcome
}

sealed interface LookupPaymentOutcome {
    data class Settled(val feesPaid: MilliSatoshi?, val preimage: ByteVector32?) :
        LookupPaymentOutcome

    data object Pending : LookupPaymentOutcome
    data class Rejected(val failure: PaymentFailure) : LookupPaymentOutcome
    data object Unknown : LookupPaymentOutcome
}

interface PaymentBackend {
    suspend fun submit(
        connection: ConnectionProfile,
        invoice: Bolt11Invoice,
        amount: MilliSatoshi
    ): SubmitPaymentOutcome

    suspend fun lookup(
        connection: ConnectionProfile,
        paymentHash: PaymentHash
    ): LookupPaymentOutcome
}

fun PaymentAttemptState.canTransitionTo(next: PaymentAttemptState): Boolean = when (this) {
    PaymentAttemptState.Created ->
        next in setOf(
            PaymentAttemptState.Submitted,
            PaymentAttemptState.Rejected
        )

    PaymentAttemptState.Submitted ->
        next in setOf(
            PaymentAttemptState.Pending,
            PaymentAttemptState.Settled,
            PaymentAttemptState.AlreadyPaid,
            PaymentAttemptState.Rejected,
            PaymentAttemptState.Unknown
        )

    PaymentAttemptState.Pending,
    PaymentAttemptState.Unknown
    ->
        next in setOf(
            PaymentAttemptState.Pending,
            PaymentAttemptState.Settled,
            PaymentAttemptState.AlreadyPaid,
            PaymentAttemptState.Rejected,
            PaymentAttemptState.Unknown
        )

    PaymentAttemptState.Settled,
    PaymentAttemptState.AlreadyPaid,
    PaymentAttemptState.Rejected
    -> false
}
