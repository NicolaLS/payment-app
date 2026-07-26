package xyz.lilsus.raylsuite.core.payment

import kotlin.jvm.JvmInline

/**
 * Provider-neutral boundary used by payment features.
 *
 * Wallet integrations translate these simple values to their own SDK types so
 * shared features never depend on Blink, NWC, or a specific Lightning library.
 */
interface PaymentProvider {
    suspend fun payInvoice(request: PayInvoiceRequest): PaidInvoice

    suspend fun lookupPayment(paymentHash: PaymentHash): PaymentLookupResult
}

data class PayInvoiceRequest(val invoice: EncodedLightningInvoice, val amountMsats: Long? = null) {
    init {
        require(amountMsats == null || amountMsats > 0) {
            "An explicit invoice amount must be greater than zero"
        }
    }
}

@JvmInline
value class EncodedLightningInvoice(val value: String) {
    init {
        require(value.isNotBlank()) { "An encoded Lightning invoice cannot be blank" }
    }
}

@JvmInline
value class PaymentHash(val hex: String) {
    init {
        require(hex.isNotBlank()) { "A payment hash cannot be blank" }
    }
}

data class PaidInvoice(
    val preimageHex: String?,
    val feesPaidMsats: Long?,
    val wasAlreadyPaid: Boolean = false
)

sealed interface PaymentLookupResult {
    data class Settled(val invoice: PaidInvoice) : PaymentLookupResult

    data object Pending : PaymentLookupResult

    data object Failed : PaymentLookupResult

    data object NotFound : PaymentLookupResult

    data class LookupError(val error: PaymentError) : PaymentLookupResult
}

sealed interface PaymentError {
    data object MissingWalletConnection : PaymentError

    data object NetworkUnavailable : PaymentError

    data class WalletConnectionFailed(val detail: String? = null) : PaymentError

    data object Timeout : PaymentError

    data class PaymentUnconfirmed(val paymentHash: PaymentHash?, val detail: String? = null) :
        PaymentError

    data class AuthenticationFailure(val detail: String? = null) : PaymentError

    data class InsufficientPermissions(val detail: String? = null) : PaymentError

    data class PaymentRejected(val code: String? = null, val detail: String? = null) : PaymentError

    data class Unexpected(val detail: String? = null) : PaymentError
}

class PaymentException(val error: PaymentError, cause: Throwable? = null) :
    Exception(error.toString(), cause)
