package xyz.lilsus.blip.feature.payment

import xyz.lilsus.blip.feature.payment.generated.resources.Res
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_amount_too_small
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_insufficient_balance
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_invalid_api_key
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_invalid_invoice
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_invoice_expired
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_limit_exceeded
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_permission_denied
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_rate_limited
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_route_not_found
import xyz.lilsus.blip.feature.payment.generated.resources.error_blink_self_payment
import xyz.lilsus.blip.feature.payment.generated.resources.error_missing_wallet_connection
import xyz.lilsus.blip.feature.payment.generated.resources.error_network_unavailable
import xyz.lilsus.blip.feature.payment.generated.resources.error_payment_rejected_generic
import xyz.lilsus.blip.feature.payment.generated.resources.error_payment_rejected_message
import xyz.lilsus.blip.feature.payment.generated.resources.error_payment_unconfirmed
import xyz.lilsus.blip.feature.payment.generated.resources.error_payment_unconfirmed_message
import xyz.lilsus.blip.integration.blink.BlinkApiError
import xyz.lilsus.blip.integration.blink.BlinkErrorType
import xyz.lilsus.raylsuite.core.ui.resources.LocalizedText
import xyz.lilsus.raylsuite.core.ui.resources.localizedTextWithOptionalDetail
import xyz.lilsus.raylsuite.feature.paymentui.exchangeRateUnavailablePaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.invalidInvoicePaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.lnurlPaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.unexpectedPaymentErrorText

suspend fun getBlipPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toLocalizedText().resolveInCoroutine()

internal fun PaymentUiError.toLocalizedText(): LocalizedText = when (this) {
    is PaymentUiError.Blink -> error.toLocalizedText()

    is PaymentUiError.InvalidInvoice -> invalidInvoicePaymentErrorText(reason)

    is PaymentUiError.Lnurl -> lnurlPaymentErrorText(reason)

    is PaymentUiError.ExchangeRateUnavailable ->
        exchangeRateUnavailablePaymentErrorText(currencyCode)

    is PaymentUiError.Unexpected -> unexpectedPaymentErrorText(detail)
}

private fun BlinkApiError.toLocalizedText(): LocalizedText = when (this) {
    BlinkApiError.MissingWalletConnection ->
        LocalizedText(Res.string.error_missing_wallet_connection)

    BlinkApiError.NetworkUnavailable ->
        LocalizedText(Res.string.error_network_unavailable)

    BlinkApiError.Timeout ->
        LocalizedText(Res.string.error_payment_unconfirmed)

    is BlinkApiError.PaymentRejected ->
        code?.toBlinkErrorType()?.toLocalizedText()
            ?: localizedTextWithOptionalDetail(
                detail = message,
                generic = Res.string.error_payment_rejected_generic,
                withDetails = Res.string.error_payment_rejected_message
            )

    is BlinkApiError.Unexpected ->
        localizedTextWithOptionalDetail(
            detail = message,
            generic = Res.string.error_payment_unconfirmed,
            withDetails = Res.string.error_payment_unconfirmed_message
        )

    is BlinkApiError.BlinkError -> type.toLocalizedText()
}

private fun BlinkErrorType.toLocalizedText(): LocalizedText = LocalizedText(
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
