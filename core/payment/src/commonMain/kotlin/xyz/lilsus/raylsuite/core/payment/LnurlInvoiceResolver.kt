package xyz.lilsus.raylsuite.core.payment

import fr.acinq.bitcoin.Crypto
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toByteVector32

class LnurlInvoiceResolver(
    private val client: LnurlPayClient,
    private val nowEpochSeconds: () -> Long = ::currentTimestampSeconds
) {
    private val inputParser = LightningInputParser()

    suspend fun resolve(request: LnurlInvoiceRequest): LnurlInvoiceResolution {
        val comment = request.comment?.takeIf(String::isNotBlank)
        if (
            comment != null &&
            (
                request.params.commentAllowed == null ||
                    comment.length > request.params.commentAllowed
                )
        ) {
            return LnurlInvoiceResolution.Failure(LnurlInvoiceResolutionError.CommentRejected)
        }

        val amountMsats = roundToFullSatoshis(request.amountMsats)
        val encodedInvoice =
            when (
                val result =
                    client.requestInvoice(
                        callback = request.params.callback,
                        amountMsats = amountMsats,
                        comment = comment
                    )
            ) {
                is LnurlResult.Success -> result.data

                is LnurlResult.Error ->
                    return LnurlInvoiceResolution.Failure(
                        LnurlInvoiceResolutionError.Client(result.error, result.cause)
                    )
            }
        val invoice =
            when (val result = inputParser.parse(encodedInvoice)) {
                is LightningInputParser.ParseResult.Success ->
                    (result.target as? LightningInputParser.Target.Bolt11)?.invoice

                is LightningInputParser.ParseResult.Failure -> null
            } ?: return LnurlInvoiceResolution.Failure(
                LnurlInvoiceResolutionError.MalformedInvoice
            )

        if (invoice.isExpired(nowEpochSeconds())) {
            return LnurlInvoiceResolution.Failure(LnurlInvoiceResolutionError.ExpiredInvoice)
        }
        if (invoice.amount?.msat != amountMsats) {
            return LnurlInvoiceResolution.Failure(
                LnurlInvoiceResolutionError.AmountMismatch(
                    expectedMsats = amountMsats,
                    actualMsats = invoice.amount?.msat
                )
            )
        }
        if (!invoice.matchesMetadata(request.params)) {
            return LnurlInvoiceResolution.Failure(
                LnurlInvoiceResolutionError.MetadataMismatch
            )
        }
        return LnurlInvoiceResolution.Success(invoice, amountMsats)
    }
}

data class LnurlInvoiceRequest(
    val params: LnurlPayParams,
    val amountMsats: Long,
    val comment: String? = null
)

sealed interface LnurlInvoiceResolution {
    data class Success(val invoice: Bolt11Invoice, val amountMsats: Long) : LnurlInvoiceResolution

    data class Failure(val error: LnurlInvoiceResolutionError) : LnurlInvoiceResolution
}

sealed interface LnurlInvoiceResolutionError {
    data class Client(val error: LnurlError, val cause: Throwable? = null) :
        LnurlInvoiceResolutionError

    data object CommentRejected : LnurlInvoiceResolutionError

    data object MalformedInvoice : LnurlInvoiceResolutionError

    data object ExpiredInvoice : LnurlInvoiceResolutionError

    data class AmountMismatch(val expectedMsats: Long, val actualMsats: Long?) :
        LnurlInvoiceResolutionError

    data object MetadataMismatch : LnurlInvoiceResolutionError
}

fun roundToFullSatoshis(msats: Long): Long =
    ((msats + MSATS_PER_SAT - 1) / MSATS_PER_SAT) * MSATS_PER_SAT

private fun Bolt11Invoice.matchesMetadata(params: LnurlPayParams): Boolean {
    description?.let { description ->
        return params.metadata.plainText?.let { it == description } ?: true
    }
    descriptionHash?.let { hash ->
        return Crypto.sha256(params.metadataRaw.encodeToByteArray()).toByteVector32() == hash
    }
    return false
}

private const val MSATS_PER_SAT = 1_000L
