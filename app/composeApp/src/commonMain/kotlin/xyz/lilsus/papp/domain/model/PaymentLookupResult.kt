package xyz.lilsus.papp.domain.model

/**
 * Result of looking up a payment by payment hash.
 */
sealed class PaymentLookupResult {
    /**
     * Payment was found and has been settled (succeeded).
     */
    data class Settled(val invoice: PaidInvoice) : PaymentLookupResult()

    /**
     * Payment is still pending (was sent but not yet confirmed).
     */
    data object Pending : PaymentLookupResult()

    /**
     * Payment failed or expired.
     */
    data object Failed : PaymentLookupResult()

    /**
     * Payment was not found (likely never sent).
     */
    data object NotFound : PaymentLookupResult()

    /**
     * Lookup failed due to an error (network, timeout, etc.).
     */
    data class LookupError(val error: AppError) : PaymentLookupResult()
}
