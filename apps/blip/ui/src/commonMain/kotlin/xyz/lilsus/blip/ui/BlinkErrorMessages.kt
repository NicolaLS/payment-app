package xyz.lilsus.blip.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.integration.blink.BlinkApiError
import xyz.lilsus.blip.integration.blink.BlinkConnectionError
import xyz.lilsus.blip.integration.blink.BlinkErrorType
import xyz.lilsus.blip.ui.generated.resources.Res
import xyz.lilsus.blip.ui.generated.resources.error_authentication_failure
import xyz.lilsus.blip.ui.generated.resources.error_blink_amount_too_small
import xyz.lilsus.blip.ui.generated.resources.error_blink_insufficient_balance
import xyz.lilsus.blip.ui.generated.resources.error_blink_invalid_api_key
import xyz.lilsus.blip.ui.generated.resources.error_blink_invalid_invoice
import xyz.lilsus.blip.ui.generated.resources.error_blink_invoice_expired
import xyz.lilsus.blip.ui.generated.resources.error_blink_limit_exceeded
import xyz.lilsus.blip.ui.generated.resources.error_blink_permission_denied
import xyz.lilsus.blip.ui.generated.resources.error_blink_rate_limited
import xyz.lilsus.blip.ui.generated.resources.error_blink_route_not_found
import xyz.lilsus.blip.ui.generated.resources.error_blink_self_payment
import xyz.lilsus.blip.ui.generated.resources.error_missing_wallet_connection
import xyz.lilsus.blip.ui.generated.resources.error_network_unavailable
import xyz.lilsus.blip.ui.generated.resources.error_payment_rejected_generic
import xyz.lilsus.blip.ui.generated.resources.error_payment_rejected_message
import xyz.lilsus.blip.ui.generated.resources.error_timeout
import xyz.lilsus.blip.ui.generated.resources.error_unexpected_generic
import xyz.lilsus.blip.ui.generated.resources.error_unexpected_with_details
import xyz.lilsus.blip.ui.generated.resources.error_wallet_already_connected

/**
 * Presents failures from Blip features outside the payment flow.
 *
 * Payment owns a separate mapping because timeout and uncertain outcomes need
 * payment-specific wording that must not be reused for ordinary wallet operations.
 */
@Composable
fun blinkErrorMessageFor(error: BlinkUiError): String = when (error) {
    is BlinkUiError.Api -> errorMessageFor(error.error)
    is BlinkUiError.Connection -> errorMessageFor(error.error)
    is BlinkUiError.Unexpected -> unexpectedErrorMessage(error.detail)
}

@Composable
private fun errorMessageFor(error: BlinkConnectionError): String = when (error) {
    BlinkConnectionError.AlreadyConnected ->
        stringResource(Res.string.error_wallet_already_connected)

    BlinkConnectionError.MissingConnection ->
        stringResource(Res.string.error_missing_wallet_connection)

    BlinkConnectionError.ApiKeyRequired ->
        stringResource(Res.string.error_authentication_failure)

    BlinkConnectionError.PaymentPermissionRequired ->
        stringResource(Res.string.error_blink_permission_denied)

    BlinkConnectionError.AliasRequired ->
        stringResource(Res.string.error_unexpected_generic)
}

@Composable
private fun errorMessageFor(error: BlinkApiError): String = when (error) {
    is BlinkApiError.BlinkError -> errorMessageFor(error.type)

    BlinkApiError.MissingWalletConnection ->
        stringResource(Res.string.error_missing_wallet_connection)

    BlinkApiError.NetworkUnavailable -> stringResource(Res.string.error_network_unavailable)

    is BlinkApiError.PaymentRejected -> {
        val message = error.message?.takeUnless(String::isBlank)
        if (message == null) {
            stringResource(Res.string.error_payment_rejected_generic)
        } else {
            stringResource(Res.string.error_payment_rejected_message, message)
        }
    }

    BlinkApiError.Timeout -> stringResource(Res.string.error_timeout)

    is BlinkApiError.Unexpected -> unexpectedErrorMessage(error.message)
}

@Composable
private fun errorMessageFor(error: BlinkErrorType): String = when (error) {
    BlinkErrorType.PermissionDenied ->
        stringResource(Res.string.error_blink_permission_denied)

    BlinkErrorType.InsufficientBalance ->
        stringResource(Res.string.error_blink_insufficient_balance)

    BlinkErrorType.RouteNotFound ->
        stringResource(Res.string.error_blink_route_not_found)

    BlinkErrorType.InvoiceExpired ->
        stringResource(Res.string.error_blink_invoice_expired)

    BlinkErrorType.SelfPayment ->
        stringResource(Res.string.error_blink_self_payment)

    BlinkErrorType.InvalidInvoice ->
        stringResource(Res.string.error_blink_invalid_invoice)

    BlinkErrorType.AmountTooSmall ->
        stringResource(Res.string.error_blink_amount_too_small)

    BlinkErrorType.LimitExceeded ->
        stringResource(Res.string.error_blink_limit_exceeded)

    BlinkErrorType.RateLimited ->
        stringResource(Res.string.error_blink_rate_limited)

    BlinkErrorType.InvalidApiKey ->
        stringResource(Res.string.error_blink_invalid_api_key)
}

@Composable
private fun unexpectedErrorMessage(detail: String?): String {
    val message = detail?.takeUnless(String::isBlank)
    return if (message == null) {
        stringResource(Res.string.error_unexpected_generic)
    } else {
        stringResource(Res.string.error_unexpected_with_details, message)
    }
}

/** Provider failures projected for Blip's reusable non-payment UI. */
sealed interface BlinkUiError {
    data class Api(val error: BlinkApiError) : BlinkUiError

    data class Connection(val error: BlinkConnectionError) : BlinkUiError

    data class Unexpected(val detail: String?) : BlinkUiError
}
