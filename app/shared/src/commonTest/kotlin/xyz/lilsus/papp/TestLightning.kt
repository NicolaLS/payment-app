package xyz.lilsus.papp

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toByteVector
import fr.acinq.lightning.utils.toByteVector32

private val baseInvoice: Bolt11Invoice =
    (Bolt11Invoice.read(BASE_INVOICE) as Try.Success).result

fun testInvoice(
    paymentRequest: String = "test-invoice",
    paymentHash: String? = null,
    amountMsats: Long? = 1_000L,
    description: String = paymentRequest
): Bolt11Invoice {
    val tags = baseInvoice.tags
        .filterNot {
            it is Bolt11Invoice.TaggedField.PaymentHash ||
                it is Bolt11Invoice.TaggedField.Description ||
                it is Bolt11Invoice.TaggedField.DescriptionHash ||
                it is Bolt11Invoice.TaggedField.PaymentMetadata
        } + listOf(
        Bolt11Invoice.TaggedField.PaymentHash(testHash(paymentHash ?: paymentRequest)),
        Bolt11Invoice.TaggedField.Description(description),
        Bolt11Invoice.TaggedField.PaymentMetadata(
            paymentRequest.encodeToByteArray().toByteVector()
        )
    )
    return Bolt11Invoice(
        prefix = baseInvoice.prefix,
        amount = amountMsats?.msat,
        timestampSeconds = baseInvoice.timestampSeconds,
        nodeId = baseInvoice.nodeId,
        tags = tags,
        signature = baseInvoice.signature
    )
}

fun testHash(value: String): ByteVector32 = runCatching { ByteVector32.fromValidHex(value) }
    .getOrElse { Crypto.sha256(value.encodeToByteArray()).toByteVector32() }

fun Bolt11Invoice.testPaymentRequest(): String = paymentMetadata?.toByteArray()?.decodeToString() ?: write()

private const val BASE_INVOICE =
    "lnbc2500u1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdq5xysxxatsyp3k7enxv4jsxqzpu9qrsgquk0rl77nj30yxdy8j9vdx85fkpmdla2087ne0xh8nhedh8w27kyke0lp53ut353s06fv3qfegext0eh0ymjpf39tuven09sam30g4vgpfna3rh"
