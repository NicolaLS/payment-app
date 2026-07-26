package xyz.lilsus.lasr.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.integration.nwc.NwcConnectionError
import xyz.lilsus.lasr.ui.generated.resources.Res
import xyz.lilsus.lasr.ui.generated.resources.error_invalid_invoice
import xyz.lilsus.lasr.ui.generated.resources.error_invalid_invoice_with_details
import xyz.lilsus.lasr.ui.generated.resources.error_invalid_wallet_uri
import xyz.lilsus.lasr.ui.generated.resources.error_lnurl
import xyz.lilsus.lasr.ui.generated.resources.error_lnurl_with_details
import xyz.lilsus.lasr.ui.generated.resources.error_missing_wallet_connection
import xyz.lilsus.lasr.ui.generated.resources.error_network_unavailable
import xyz.lilsus.lasr.ui.generated.resources.error_payment_rejected_generic
import xyz.lilsus.lasr.ui.generated.resources.error_payment_rejected_message
import xyz.lilsus.lasr.ui.generated.resources.error_payment_unconfirmed
import xyz.lilsus.lasr.ui.generated.resources.error_payment_unconfirmed_message
import xyz.lilsus.lasr.ui.generated.resources.error_relay_connection_failed
import xyz.lilsus.lasr.ui.generated.resources.error_timeout
import xyz.lilsus.lasr.ui.generated.resources.error_unexpected_generic
import xyz.lilsus.lasr.ui.generated.resources.error_unexpected_with_details
import xyz.lilsus.lasr.ui.generated.resources.error_wallet_already_connected
import xyz.lilsus.raylsuite.core.payment.PaymentError
import xyz.lilsus.raylsuite.feature.payment.PaymentUiError

@Composable
fun lasrConnectionErrorMessageFor(error: NwcConnectionError): String = error.toMessage().resolve()

@Composable
fun lasrPaymentErrorMessageFor(error: PaymentUiError): String = error.toMessage().resolve()

suspend fun getLasrPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toMessage().resolveInCoroutine()

private fun NwcConnectionError.toMessage(): LocalizedMessage = when (this) {
    NwcConnectionError.AlreadyConnected ->
        LocalizedMessage(Res.string.error_wallet_already_connected)

    NwcConnectionError.InvalidUri ->
        LocalizedMessage(Res.string.error_invalid_wallet_uri)

    NwcConnectionError.PaymentPermissionRequired ->
        LocalizedMessage(Res.string.error_payment_rejected_generic)

    is NwcConnectionError.ConnectionFailed ->
        LocalizedMessage(Res.string.error_relay_connection_failed)
}

private fun PaymentUiError.toMessage(): LocalizedMessage = when (this) {
    is PaymentUiError.Provider -> error.toMessage()

    is PaymentUiError.InvalidInvoice ->
        optionalDetailMessage(
            detail = reason,
            generic = Res.string.error_invalid_invoice,
            withDetails = Res.string.error_invalid_invoice_with_details
        )

    is PaymentUiError.Lnurl ->
        optionalDetailMessage(
            detail = reason,
            generic = Res.string.error_lnurl,
            withDetails = Res.string.error_lnurl_with_details
        )

    is PaymentUiError.Unexpected ->
        optionalDetailMessage(
            detail = detail,
            generic = Res.string.error_unexpected_generic,
            withDetails = Res.string.error_unexpected_with_details
        )
}

private fun PaymentError.toMessage(): LocalizedMessage = when (this) {
    PaymentError.MissingWalletConnection ->
        LocalizedMessage(Res.string.error_missing_wallet_connection)

    PaymentError.NetworkUnavailable ->
        LocalizedMessage(Res.string.error_network_unavailable)

    is PaymentError.WalletConnectionFailed ->
        LocalizedMessage(Res.string.error_relay_connection_failed)

    PaymentError.Timeout ->
        LocalizedMessage(Res.string.error_timeout)

    is PaymentError.PaymentUnconfirmed ->
        optionalDetailMessage(
            detail = detail,
            generic = Res.string.error_payment_unconfirmed,
            withDetails = Res.string.error_payment_unconfirmed_message
        )

    is PaymentError.PaymentRejected ->
        optionalDetailMessage(
            detail = detail,
            generic = Res.string.error_payment_rejected_generic,
            withDetails = Res.string.error_payment_rejected_message
        )

    is PaymentError.AuthenticationFailure,
    is PaymentError.InsufficientPermissions,
    is PaymentError.Unexpected ->
        optionalDetailMessage(
            detail =
                when (this) {
                    is PaymentError.AuthenticationFailure -> detail
                    is PaymentError.InsufficientPermissions -> detail
                    is PaymentError.Unexpected -> detail
                },
            generic = Res.string.error_unexpected_generic,
            withDetails = Res.string.error_unexpected_with_details
        )
}

private fun optionalDetailMessage(
    detail: String?,
    generic: StringResource,
    withDetails: StringResource
): LocalizedMessage {
    val argument = detail?.takeUnless(String::isBlank)
    return if (argument == null) {
        LocalizedMessage(generic)
    } else {
        LocalizedMessage(withDetails, argument)
    }
}

private data class LocalizedMessage(val resource: StringResource, val argument: String? = null)

@Composable
private fun LocalizedMessage.resolve(): String = if (argument == null) {
    stringResource(resource)
} else {
    stringResource(resource, argument)
}

private suspend fun LocalizedMessage.resolveInCoroutine(): String = if (argument == null) {
    getString(resource)
} else {
    getString(resource, argument)
}
