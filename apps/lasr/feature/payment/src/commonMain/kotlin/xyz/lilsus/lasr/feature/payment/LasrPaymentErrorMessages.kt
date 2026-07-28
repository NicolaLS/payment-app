package xyz.lilsus.lasr.feature.payment

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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

@Composable
fun lasrPaymentErrorMessageFor(error: PaymentUiError): String = error.toMessage().resolve()

suspend fun getLasrPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toMessage().resolveInCoroutine()

private fun PaymentUiError.toMessage(): LocalizedMessage = when (this) {
    is PaymentUiError.Nwc -> when (val nwcError = error) {
        NwcPaymentError.MissingWalletConnection ->
            LocalizedMessage(Res.string.error_missing_wallet_connection)

        NwcPaymentError.NetworkUnavailable ->
            LocalizedMessage(Res.string.error_network_unavailable)

        is NwcPaymentError.Connection ->
            LocalizedMessage(Res.string.error_relay_connection_failed)

        is NwcPaymentError.Rejected ->
            optionalDetailMessage(
                nwcError.detail ?: nwcError.code,
                Res.string.error_payment_rejected_generic,
                Res.string.error_payment_rejected_message
            )

        is NwcPaymentError.OutcomeUnknown ->
            optionalDetailMessage(
                nwcError.detail,
                Res.string.error_payment_unconfirmed,
                Res.string.error_payment_unconfirmed_message
            )

        is NwcPaymentError.DefinitelyNotSent,
        is NwcPaymentError.Unexpected ->
            optionalDetailMessage(
                nwcError.detail,
                Res.string.error_unexpected_generic,
                Res.string.error_unexpected_with_details
            )
    }

    is PaymentUiError.InvalidInvoice ->
        optionalDetailMessage(
            reason,
            Res.string.error_invalid_invoice,
            Res.string.error_invalid_invoice_with_details
        )

    is PaymentUiError.Lnurl ->
        optionalDetailMessage(
            reason,
            Res.string.error_lnurl,
            Res.string.error_lnurl_with_details
        )

    is PaymentUiError.Unexpected ->
        optionalDetailMessage(
            detail,
            Res.string.error_unexpected_generic,
            Res.string.error_unexpected_with_details
        )
}

private fun optionalDetailMessage(
    detail: String?,
    generic: StringResource,
    withDetails: StringResource
): LocalizedMessage = detail?.takeUnless(String::isBlank)
    ?.let { LocalizedMessage(withDetails, it) }
    ?: LocalizedMessage(generic)

private data class LocalizedMessage(val resource: StringResource, val argument: String? = null)

@Composable
private fun LocalizedMessage.resolve(): String =
    argument?.let { stringResource(resource, it) } ?: stringResource(resource)

private suspend fun LocalizedMessage.resolveInCoroutine(): String =
    argument?.let { getString(resource, it) } ?: getString(resource)
