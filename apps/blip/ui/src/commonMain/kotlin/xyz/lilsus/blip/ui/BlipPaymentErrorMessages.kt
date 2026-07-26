package xyz.lilsus.blip.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.integration.blink.BlinkErrorType
import xyz.lilsus.blip.ui.generated.resources.Res
import xyz.lilsus.blip.ui.generated.resources.error_authentication_failure
import xyz.lilsus.blip.ui.generated.resources.error_authentication_failure_message
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
import xyz.lilsus.blip.ui.generated.resources.error_invalid_invoice
import xyz.lilsus.blip.ui.generated.resources.error_invalid_invoice_with_details
import xyz.lilsus.blip.ui.generated.resources.error_lnurl
import xyz.lilsus.blip.ui.generated.resources.error_lnurl_with_details
import xyz.lilsus.blip.ui.generated.resources.error_missing_wallet_connection
import xyz.lilsus.blip.ui.generated.resources.error_network_unavailable
import xyz.lilsus.blip.ui.generated.resources.error_payment_rejected_generic
import xyz.lilsus.blip.ui.generated.resources.error_payment_rejected_message
import xyz.lilsus.blip.ui.generated.resources.error_payment_unconfirmed
import xyz.lilsus.blip.ui.generated.resources.error_payment_unconfirmed_message
import xyz.lilsus.blip.ui.generated.resources.error_relay_connection_failed
import xyz.lilsus.blip.ui.generated.resources.error_timeout
import xyz.lilsus.blip.ui.generated.resources.error_unexpected_generic
import xyz.lilsus.blip.ui.generated.resources.error_unexpected_with_details
import xyz.lilsus.raylsuite.core.payment.PaymentError
import xyz.lilsus.raylsuite.feature.payment.PaymentUiError

@Composable
fun blipPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toMessage().resolve()

suspend fun getBlipPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toMessage().resolveInCoroutine()

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

    is PaymentError.AuthenticationFailure ->
        optionalDetailMessage(
            detail = detail,
            generic = Res.string.error_authentication_failure,
            withDetails = Res.string.error_authentication_failure_message
        )

    is PaymentError.InsufficientPermissions ->
        LocalizedMessage(Res.string.error_blink_permission_denied)

    is PaymentError.PaymentRejected ->
        code?.toBlinkErrorType()?.toMessage()
            ?: optionalDetailMessage(
                detail = detail,
                generic = Res.string.error_payment_rejected_generic,
                withDetails = Res.string.error_payment_rejected_message
            )

    is PaymentError.Unexpected ->
        optionalDetailMessage(
            detail = detail,
            generic = Res.string.error_unexpected_generic,
            withDetails = Res.string.error_unexpected_with_details
        )
}

private fun BlinkErrorType.toMessage(): LocalizedMessage = LocalizedMessage(
    when (this) {
        BlinkErrorType.PermissionDenied -> Res.string.error_blink_permission_denied
        BlinkErrorType.InsufficientBalance -> Res.string.error_blink_insufficient_balance
        BlinkErrorType.RouteNotFound -> Res.string.error_blink_route_not_found
        BlinkErrorType.InvoiceExpired -> Res.string.error_blink_invoice_expired
        BlinkErrorType.SelfPayment -> Res.string.error_blink_self_payment
        BlinkErrorType.InvalidInvoice -> Res.string.error_blink_invalid_invoice
        BlinkErrorType.AmountTooSmall -> Res.string.error_blink_amount_too_small
        BlinkErrorType.LimitExceeded -> Res.string.error_blink_limit_exceeded
        BlinkErrorType.RateLimited -> Res.string.error_blink_rate_limited
        BlinkErrorType.InvalidApiKey -> Res.string.error_blink_invalid_api_key
    }
)

private fun String.toBlinkErrorType(): BlinkErrorType? =
    BlinkErrorType.entries.firstOrNull { it.name == this }

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

private data class LocalizedMessage(
    val resource: StringResource,
    val argument: String? = null
)

@Composable
private fun LocalizedMessage.resolve(): String =
    if (argument == null) {
        stringResource(resource)
    } else {
        stringResource(resource, argument)
    }

private suspend fun LocalizedMessage.resolveInCoroutine(): String =
    if (argument == null) {
        getString(resource)
    } else {
        getString(resource, argument)
    }
