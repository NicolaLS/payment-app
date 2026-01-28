package xyz.lilsus.papp.presentation.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.error_authentication_failure
import papp.composeapp.generated.resources.error_authentication_failure_message
import papp.composeapp.generated.resources.error_blink_amount_too_small
import papp.composeapp.generated.resources.error_blink_insufficient_balance
import papp.composeapp.generated.resources.error_blink_invalid_api_key
import papp.composeapp.generated.resources.error_blink_invalid_api_key_wallet_removed
import papp.composeapp.generated.resources.error_blink_invalid_invoice
import papp.composeapp.generated.resources.error_blink_invoice_expired
import papp.composeapp.generated.resources.error_blink_limit_exceeded
import papp.composeapp.generated.resources.error_blink_permission_denied
import papp.composeapp.generated.resources.error_blink_rate_limited
import papp.composeapp.generated.resources.error_blink_route_not_found
import papp.composeapp.generated.resources.error_blink_self_payment
import papp.composeapp.generated.resources.error_invalid_invoice
import papp.composeapp.generated.resources.error_invalid_invoice_with_details
import papp.composeapp.generated.resources.error_invalid_wallet_uri
import papp.composeapp.generated.resources.error_missing_wallet_connection
import papp.composeapp.generated.resources.error_network_unavailable
import papp.composeapp.generated.resources.error_payment_rejected_generic
import papp.composeapp.generated.resources.error_payment_rejected_message
import papp.composeapp.generated.resources.error_payment_unconfirmed
import papp.composeapp.generated.resources.error_payment_unconfirmed_message
import papp.composeapp.generated.resources.error_relay_connection_failed
import papp.composeapp.generated.resources.error_timeout
import papp.composeapp.generated.resources.error_unexpected_generic
import papp.composeapp.generated.resources.error_unexpected_with_details
import papp.composeapp.generated.resources.error_unrecognized_input
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.BlinkErrorType

/**
 * Resolves a human-readable message for the provided [AppError] using localized string resources.
 */
@Composable
fun errorMessageFor(error: AppError): String = when (error) {
    AppError.MissingWalletConnection -> stringResource(Res.string.error_missing_wallet_connection)

    is AppError.PaymentRejected -> {
        val message = error.message?.takeUnless { it.isBlank() }
        if (message != null) {
            stringResource(Res.string.error_payment_rejected_message, message)
        } else {
            stringResource(Res.string.error_payment_rejected_generic)
        }
    }

    AppError.NetworkUnavailable -> stringResource(Res.string.error_network_unavailable)

    is AppError.RelayConnectionFailed -> stringResource(Res.string.error_relay_connection_failed)

    AppError.Timeout -> stringResource(Res.string.error_timeout)

    is AppError.PaymentUnconfirmed -> {
        val details = error.message?.takeUnless { it.isBlank() }
        if (details != null) {
            stringResource(Res.string.error_payment_unconfirmed_message, details)
        } else {
            stringResource(Res.string.error_payment_unconfirmed)
        }
    }

    is AppError.AuthenticationFailure -> {
        val details = error.message?.takeUnless { it.isBlank() }
        if (details != null) {
            stringResource(Res.string.error_authentication_failure_message, details)
        } else {
            stringResource(Res.string.error_authentication_failure)
        }
    }

    is AppError.InsufficientPermissions -> {
        stringResource(Res.string.error_blink_permission_denied)
    }

    is AppError.InvalidWalletUri -> stringResource(Res.string.error_invalid_wallet_uri)

    is AppError.UnrecognizedInput -> stringResource(Res.string.error_unrecognized_input)

    is AppError.InvalidInvoice -> {
        val details = error.reason?.takeUnless { it.isBlank() }
        if (details != null) {
            stringResource(Res.string.error_invalid_invoice_with_details, details)
        } else {
            stringResource(Res.string.error_invalid_invoice)
        }
    }

    is AppError.Unexpected -> {
        val details = error.message?.takeUnless { it.isBlank() }
        if (details != null) {
            stringResource(Res.string.error_unexpected_with_details, details)
        } else {
            stringResource(Res.string.error_unexpected_generic)
        }
    }

    is AppError.BlinkError -> when (error.type) {
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

        BlinkErrorType.InvalidApiKeyWalletRemoved ->
            stringResource(Res.string.error_blink_invalid_api_key_wallet_removed)
    }
}
