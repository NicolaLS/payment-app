package xyz.lilsus.blip.ui

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
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
import xyz.lilsus.blip.ui.generated.resources.error_blink_required_permissions
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
 * This suspending entry point resolves the app-owned presentation for native renderers.
 */
suspend fun nativeBlinkErrorMessageFor(error: BlinkUiError): String = error.text().resolveNative()

internal fun BlinkUiError.text(): BlinkErrorText = when (this) {
    is BlinkUiError.Api -> error.text()
    is BlinkUiError.Connection -> error.text()
    is BlinkUiError.Unexpected -> unexpectedErrorText(detail)
}

private fun BlinkConnectionError.text(): BlinkErrorText = when (this) {
    BlinkConnectionError.AlreadyConnected ->
        BlinkErrorText.Plain(Res.string.error_wallet_already_connected)

    BlinkConnectionError.MissingConnection ->
        BlinkErrorText.Plain(Res.string.error_missing_wallet_connection)

    BlinkConnectionError.ApiKeyRequired ->
        BlinkErrorText.Plain(Res.string.error_authentication_failure)

    BlinkConnectionError.RequiredPermissionsMissing ->
        BlinkErrorText.Plain(Res.string.error_blink_required_permissions)
}

private fun BlinkApiError.text(): BlinkErrorText = when (this) {
    is BlinkApiError.BlinkError -> type.text()

    BlinkApiError.MissingWalletConnection ->
        BlinkErrorText.Plain(Res.string.error_missing_wallet_connection)

    BlinkApiError.NetworkUnavailable -> BlinkErrorText.Plain(Res.string.error_network_unavailable)

    is BlinkApiError.PaymentRejected -> {
        val message = this.message?.takeUnless(String::isBlank)
        if (message == null) {
            BlinkErrorText.Plain(Res.string.error_payment_rejected_generic)
        } else {
            BlinkErrorText.Formatted(Res.string.error_payment_rejected_message, message)
        }
    }

    BlinkApiError.Timeout -> BlinkErrorText.Plain(Res.string.error_timeout)

    is BlinkApiError.Unexpected -> unexpectedErrorText(this.message)
}

private fun BlinkErrorType.text(): BlinkErrorText = when (this) {
    BlinkErrorType.PermissionDenied ->
        BlinkErrorText.Plain(Res.string.error_blink_permission_denied)

    BlinkErrorType.InsufficientBalance ->
        BlinkErrorText.Plain(Res.string.error_blink_insufficient_balance)

    BlinkErrorType.RouteNotFound ->
        BlinkErrorText.Plain(Res.string.error_blink_route_not_found)

    BlinkErrorType.InvoiceExpired ->
        BlinkErrorText.Plain(Res.string.error_blink_invoice_expired)

    BlinkErrorType.SelfPayment ->
        BlinkErrorText.Plain(Res.string.error_blink_self_payment)

    BlinkErrorType.InvalidInvoice ->
        BlinkErrorText.Plain(Res.string.error_blink_invalid_invoice)

    BlinkErrorType.AmountTooSmall ->
        BlinkErrorText.Plain(Res.string.error_blink_amount_too_small)

    BlinkErrorType.LimitExceeded ->
        BlinkErrorText.Plain(Res.string.error_blink_limit_exceeded)

    BlinkErrorType.RateLimited ->
        BlinkErrorText.Plain(Res.string.error_blink_rate_limited)

    BlinkErrorType.InvalidApiKey ->
        BlinkErrorText.Plain(Res.string.error_blink_invalid_api_key)
}

private fun unexpectedErrorText(detail: String?): BlinkErrorText {
    val message = detail?.takeUnless(String::isBlank)
    return if (message == null) {
        BlinkErrorText.Plain(Res.string.error_unexpected_generic)
    } else {
        BlinkErrorText.Formatted(Res.string.error_unexpected_with_details, message)
    }
}

private suspend fun BlinkErrorText.resolveNative(): String = when (this) {
    is BlinkErrorText.Plain -> getString(resource)
    is BlinkErrorText.Formatted -> getString(resource, argument)
}

internal sealed interface BlinkErrorText {
    data class Plain(val resource: StringResource) : BlinkErrorText

    data class Formatted(val resource: StringResource, val argument: String) : BlinkErrorText
}

/** Provider failures projected for Blip's reusable non-payment UI. */
sealed interface BlinkUiError {
    data class Api(val error: BlinkApiError) : BlinkUiError

    data class Connection(val error: BlinkConnectionError) : BlinkUiError

    data class Unexpected(val detail: String?) : BlinkUiError
}
