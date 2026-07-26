package xyz.lilsus.blip.domain.usecases

import fr.acinq.bitcoin.ByteVector32
import xyz.lilsus.blip.domain.model.PaymentLookupResult
import xyz.lilsus.blip.domain.repository.PaymentProvider

/**
 * Use case for looking up the status of a payment by its payment hash.
 * Used to verify pending payments when re-scanning the same invoice.
 */
class LookupPaymentUseCase(private val paymentProvider: PaymentProvider) {
    /**
     * Looks up the status of a payment by its payment hash.
     *
     * @param paymentHash The payment hash from the BOLT11 invoice.
     * @return The [PaymentLookupResult] indicating the payment status.
     */
    suspend operator fun invoke(paymentHash: ByteVector32): PaymentLookupResult =
        paymentProvider.lookupPayment(paymentHash)
}
