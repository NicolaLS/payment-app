package xyz.lilsus.blip.feature.payment

import xyz.lilsus.blip.integration.blink.BlinkApiError
import xyz.lilsus.blip.integration.blink.BlinkApiException
import xyz.lilsus.blip.integration.blink.BlinkConnectionException
import xyz.lilsus.raylsuite.core.payment.LnurlError
import xyz.lilsus.raylsuite.core.payment.LnurlInvoiceResolutionError
import xyz.lilsus.raylsuite.feature.paymentcurrency.CurrencyManagerError

internal fun Throwable.toPaymentUiError(): PaymentUiError = when (this) {
    is BlinkApiException -> PaymentUiError.Blink(error)

    is BlinkConnectionException ->
        PaymentUiError.Blink(BlinkApiError.MissingWalletConnection)

    else -> PaymentUiError.Unexpected(message)
}

internal fun CurrencyManagerError.toPaymentUiError(): PaymentUiError = when (this) {
    is CurrencyManagerError.ExchangeRateUnavailable ->
        PaymentUiError.ExchangeRateUnavailable(currencyCode)
}

internal fun LnurlError.toPaymentUiError(): PaymentUiError = when (this) {
    LnurlError.NetworkUnavailable ->
        PaymentUiError.Blink(BlinkApiError.NetworkUnavailable)

    is LnurlError.Protocol -> PaymentUiError.Lnurl(reason)

    is LnurlError.Unexpected -> PaymentUiError.Lnurl(detail)
}

internal fun LnurlInvoiceResolutionError.toPaymentUiError(): PaymentUiError = when (this) {
    is LnurlInvoiceResolutionError.Client -> error.toPaymentUiError()

    LnurlInvoiceResolutionError.CommentRejected ->
        PaymentUiError.InvalidInvoice("Description is too long for this address")

    LnurlInvoiceResolutionError.MalformedInvoice ->
        PaymentUiError.InvalidInvoice("Failed to parse BOLT11 invoice")

    LnurlInvoiceResolutionError.ExpiredInvoice ->
        PaymentUiError.InvalidInvoice("Invoice has expired")

    is LnurlInvoiceResolutionError.AmountMismatch ->
        PaymentUiError.InvalidInvoice("LNURL invoice amount does not match")

    LnurlInvoiceResolutionError.MetadataMismatch ->
        PaymentUiError.InvalidInvoice("LNURL invoice metadata mismatch")
}
