package xyz.lilsus.papp.domain.usecases

import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.repository.PaymentProvider

/**
 * Use case responsible for paying a Lightning invoice via the connected wallet.
 * Routes to the appropriate payment provider (NWC or Blink) based on the active wallet.
 */
class PayInvoiceUseCase(private val paymentProvider: PaymentProvider) {
    /**
     * Starts a pay request for the provided [invoice] and returns a handle that can be observed
     * for completion. This returns immediately with a request in Loading state; the actual
     * payment happens in the background. Callers should cancel the request when they no longer
     * need updates.
     */
    operator fun invoke(invoice: String, amountMsats: Long? = null): PayInvoiceRequest =
        paymentProvider.startPayInvoiceRequest(
            invoice = invoice,
            amountMsats = amountMsats
        )
}
