package xyz.lilsus.papp.domain.model

/**
 * Domain representation of a paid Lightning invoice.
 *
 * @param preimage Payment preimage returned by the wallet, or null if not provided.
 * @param feesPaidMsats Fees paid in millisatoshis, or null if not reported.
 * @param wasAlreadyPaid True when the wallet reports this invoice was already paid and
 * no new payment was sent by this app.
 */
data class PaidInvoice(
    val preimage: String?,
    val feesPaidMsats: Long?,
    val wasAlreadyPaid: Boolean = false
)
