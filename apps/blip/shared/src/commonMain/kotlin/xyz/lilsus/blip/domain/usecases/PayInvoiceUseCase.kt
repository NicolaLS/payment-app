package xyz.lilsus.blip.domain.usecases

import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice
import xyz.lilsus.blip.domain.model.PayInvoiceRequest
import xyz.lilsus.blip.domain.repository.PaymentProvider

/**
 * Use case responsible for paying a Lightning invoice via the connected wallet.
 * Routes to the appropriate payment provider (NWC or Blink) based on the connected wallet.
 */
class PayInvoiceUseCase(private val paymentProvider: PaymentProvider) {
    /**
     * Starts a pay request for the provided [invoice] and returns a handle that can be observed
     * for completion. This returns immediately with a request in Loading state; the actual
     * payment happens in the background. Callers should cancel the request when they no longer
     * need updates.
     */
    operator fun invoke(invoice: Bolt11Invoice, amount: MilliSatoshi? = null): PayInvoiceRequest =
        paymentProvider.startPayInvoiceRequest(
            invoice = invoice,
            amount = amount
        )
}
