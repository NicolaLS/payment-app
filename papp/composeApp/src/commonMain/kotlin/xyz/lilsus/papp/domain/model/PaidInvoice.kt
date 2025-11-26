package xyz.lilsus.papp.domain.model

/**
 * Domain representation of a paid Lightning invoice.
 *
 * @param preimage Payment preimage returned by the NWC wallet.
 * @param feesPaidMsats Optional fees paid in millisatoshis.
 */
data class PaidInvoice(val preimage: String, val feesPaidMsats: Long?)
