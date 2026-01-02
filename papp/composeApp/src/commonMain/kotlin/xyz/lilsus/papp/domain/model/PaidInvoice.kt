package xyz.lilsus.papp.domain.model

/**
 * Domain representation of a paid Lightning invoice.
 *
 * @param preimage Payment preimage returned by the wallet, or null if not provided.
 * @param feesPaidMsats Fees paid in millisatoshis, or null if not reported.
 */
data class PaidInvoice(val preimage: String?, val feesPaidMsats: Long?)
