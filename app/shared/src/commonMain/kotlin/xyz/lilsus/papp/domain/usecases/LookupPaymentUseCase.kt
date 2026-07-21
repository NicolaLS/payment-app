package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.repository.PaymentProvider

/**
 * Use case for looking up the status of a payment by its payment hash.
 * Used to verify pending payments when re-scanning the same invoice.
 */
class LookupPaymentUseCase(private val paymentProvider: PaymentProvider) {
    /**
     * Looks up the status of a payment by its payment hash.
     *
     * @param paymentHash The hex-encoded payment hash from the BOLT11 invoice.
     * @return The [PaymentLookupResult] indicating the payment status.
     */
    suspend operator fun invoke(paymentHash: String): PaymentLookupResult =
        paymentProvider.lookupPayment(paymentHash)
}
