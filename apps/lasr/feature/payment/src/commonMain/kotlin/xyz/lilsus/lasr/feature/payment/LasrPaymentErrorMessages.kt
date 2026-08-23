package xyz.lilsus.lasr.feature.payment

import androidx.compose.runtime.Composable
import xyz.lilsus.lasr.feature.payment.generated.resources.Res
import xyz.lilsus.lasr.feature.payment.generated.resources.error_missing_wallet_connection
import xyz.lilsus.lasr.feature.payment.generated.resources.error_network_unavailable
import xyz.lilsus.lasr.feature.payment.generated.resources.error_payment_rejected_generic
import xyz.lilsus.lasr.feature.payment.generated.resources.error_payment_rejected_message
import xyz.lilsus.lasr.feature.payment.generated.resources.error_payment_unconfirmed
import xyz.lilsus.lasr.feature.payment.generated.resources.error_payment_unconfirmed_message
import xyz.lilsus.lasr.feature.payment.generated.resources.error_relay_connection_failed
import xyz.lilsus.raylsuite.core.ui.resources.LocalizedText
import xyz.lilsus.raylsuite.core.ui.resources.localizedTextWithOptionalDetail
import xyz.lilsus.raylsuite.feature.paymentui.exchangeRateUnavailablePaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.invalidInvoicePaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.lnurlPaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.unexpectedPaymentErrorText

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
        is NwcPaymentError.Unexpected -> unexpectedPaymentErrorText(nwcError.detail)
    }

    is PaymentUiError.InvalidInvoice -> invalidInvoicePaymentErrorText(reason)

    is PaymentUiError.Lnurl -> lnurlPaymentErrorText(reason)

    is PaymentUiError.ExchangeRateUnavailable ->
        exchangeRateUnavailablePaymentErrorText(currencyCode)

    is PaymentUiError.Unexpected -> unexpectedPaymentErrorText(detail)
}
