package xyz.lilsus.papp.domain.repository

import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PaymentLookupResult

/**
 * Abstraction for wallet payment operations.
 * Implementations handle the specifics of different wallet backends (NWC, Blink, etc.).
 */
interface PaymentProvider {
    /**
     * Starts a payment request for the given invoice.
     * Returns immediately with a request in Loading state; the actual payment happens
     * in the background. Callers should observe the request state and cancel when done.
     *
     * @param invoice The BOLT11 invoice to pay.
     * @param amountMsats Optional amount in millisatoshis (required for zero-amount invoices).
     * @return A [PayInvoiceRequest] that can be observed for completion.
     */
    fun startPayInvoiceRequest(invoice: String, amountMsats: Long? = null): PayInvoiceRequest

    /**
     * Pays an invoice and suspends until completion or failure.
     *
     * @param invoice The BOLT11 invoice to pay.
     * @param amountMsats Optional amount in millisatoshis (required for zero-amount invoices).
     * @return The [PaidInvoice] result on success.
     * @throws xyz.lilsus.papp.domain.model.AppErrorException on failure.
     */
    suspend fun payInvoice(invoice: String, amountMsats: Long? = null): PaidInvoice

    /**
     * Looks up the status of a payment by payment hash.
     *
     * @param paymentHash The hex-encoded payment hash from the BOLT11 invoice.
     * @return The [PaymentLookupResult] indicating the payment status.
     */
    suspend fun lookupPayment(paymentHash: String): PaymentLookupResult
}
