package xyz.lilsus.lasr.feature.payment

import androidx.compose.runtime.Composable
import xyz.lilsus.lasr.feature.payment.generated.resources.Res
import xyz.lilsus.lasr.feature.payment.generated.resources.error_invalid_invoice
import xyz.lilsus.lasr.feature.payment.generated.resources.error_invalid_invoice_with_details
import xyz.lilsus.lasr.feature.payment.generated.resources.error_lnurl
import xyz.lilsus.lasr.feature.payment.generated.resources.error_lnurl_with_details
import xyz.lilsus.lasr.feature.payment.generated.resources.error_missing_wallet_connection
import xyz.lilsus.lasr.feature.payment.generated.resources.error_network_unavailable
import xyz.lilsus.lasr.feature.payment.generated.resources.error_payment_rejected_generic
import xyz.lilsus.lasr.feature.payment.generated.resources.error_payment_rejected_message
import xyz.lilsus.lasr.feature.payment.generated.resources.error_payment_unconfirmed
import xyz.lilsus.lasr.feature.payment.generated.resources.error_payment_unconfirmed_message
import xyz.lilsus.lasr.feature.payment.generated.resources.error_relay_connection_failed
import xyz.lilsus.lasr.feature.payment.generated.resources.error_unexpected_generic
import xyz.lilsus.lasr.feature.payment.generated.resources.error_unexpected_with_details
import xyz.lilsus.raylsuite.core.ui.resources.LocalizedText
import xyz.lilsus.raylsuite.core.ui.resources.localizedTextWithOptionalDetail

@Composable
fun lasrPaymentErrorMessageFor(error: PaymentUiError): String = error.toLocalizedText().resolve()

suspend fun getLasrPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toLocalizedText().resolveInCoroutine()

private fun PaymentUiError.toLocalizedText(): LocalizedText = when (this) {
    is PaymentUiError.Nwc -> when (val nwcError = error) {
        NwcPaymentError.MissingWalletConnection ->
            LocalizedText(Res.string.error_missing_wallet_connection)

        NwcPaymentError.NetworkUnavailable ->
            LocalizedText(Res.string.error_network_unavailable)

        is NwcPaymentError.Connection ->
            LocalizedText(Res.string.error_relay_connection_failed)

        is NwcPaymentError.Rejected ->
            localizedTextWithOptionalDetail(
                nwcError.detail ?: nwcError.code,
                Res.string.error_payment_rejected_generic,
                Res.string.error_payment_rejected_message
            )

        is NwcPaymentError.OutcomeUnknown ->
            localizedTextWithOptionalDetail(
                nwcError.detail,
                Res.string.error_payment_unconfirmed,
                Res.string.error_payment_unconfirmed_message
            )

        is NwcPaymentError.DefinitelyNotSent,
        is NwcPaymentError.Unexpected ->
            localizedTextWithOptionalDetail(
                nwcError.detail,
                Res.string.error_unexpected_generic,
                Res.string.error_unexpected_with_details
            )
    }

    is PaymentUiError.InvalidInvoice ->
        localizedTextWithOptionalDetail(
            reason,
            Res.string.error_invalid_invoice,
            Res.string.error_invalid_invoice_with_details
        )

    is PaymentUiError.Lnurl ->
        localizedTextWithOptionalDetail(
            reason,
            Res.string.error_lnurl,
            Res.string.error_lnurl_with_details
        )

    is PaymentUiError.Unexpected ->
        localizedTextWithOptionalDetail(
            detail,
            Res.string.error_unexpected_generic,
            Res.string.error_unexpected_with_details
        )
}
