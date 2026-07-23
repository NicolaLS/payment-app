package xyz.lilsus.papp.domain.model

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi

/**
 * Domain representation of a paid Lightning invoice.
 *
 * @param preimage Payment preimage returned by the wallet, or null if not provided.
 * @param feesPaid Fees paid, or null if not reported.
 * @param wasAlreadyPaid True when the wallet reports this invoice was already paid and
 * no new payment was sent by this app.
 */
data class PaidInvoice(
    val preimage: ByteVector32?,
    val feesPaid: MilliSatoshi?,
    val wasAlreadyPaid: Boolean = false
)
