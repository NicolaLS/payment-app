package xyz.lilsus.flint.feature.payment

import xyz.lilsus.flint.feature.payment.generated.resources.Res
import xyz.lilsus.flint.feature.payment.generated.resources.error_missing_wallet_connection
import xyz.lilsus.flint.feature.payment.generated.resources.error_payment_rejected_generic
import xyz.lilsus.flint.feature.payment.generated.resources.error_payment_rejected_message
import xyz.lilsus.flint.feature.payment.generated.resources.error_payment_unconfirmed
import xyz.lilsus.flint.feature.payment.generated.resources.error_payment_unconfirmed_message
import xyz.lilsus.flint.feature.payment.generated.resources.error_relay_connection_failed
import xyz.lilsus.raylsuite.core.ui.resources.LocalizedText
import xyz.lilsus.raylsuite.core.ui.resources.localizedTextWithOptionalDetail
import xyz.lilsus.raylsuite.feature.paymentui.exchangeRateUnavailablePaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.invalidInvoicePaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.lnurlPaymentErrorText
import xyz.lilsus.raylsuite.feature.paymentui.unexpectedPaymentErrorText

suspend fun getFlintPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toLocalizedText().resolveInCoroutine()

internal fun PaymentUiError.toLocalizedText(): LocalizedText = when (this) {
    is PaymentUiError.Spark -> when (val sparkError = error) {
        SparkPaymentError.WalletUnavailable ->
            LocalizedText(Res.string.error_missing_wallet_connection)

        SparkPaymentError.SdkUnavailable ->
            LocalizedText(Res.string.error_relay_connection_failed)

        SparkPaymentError.StorageUnavailable,
        is SparkPaymentError.Unexpected -> unexpectedPaymentErrorText(sparkError.detail)

        SparkPaymentError.CapacityReached ->
            LocalizedText(Res.string.error_payment_rejected_generic)

        is SparkPaymentError.Rejected ->
            localizedTextWithOptionalDetail(
                sparkError.detail,
                Res.string.error_payment_rejected_generic,
                Res.string.error_payment_rejected_message
            )

        is SparkPaymentError.OutcomeUnknown ->
            localizedTextWithOptionalDetail(
                sparkError.detail,
                Res.string.error_payment_unconfirmed,
                Res.string.error_payment_unconfirmed_message
            )
    }

    is PaymentUiError.InvalidInvoice -> invalidInvoicePaymentErrorText(reason)

    is PaymentUiError.Lnurl -> lnurlPaymentErrorText(reason)

    is PaymentUiError.ExchangeRateUnavailable ->
        exchangeRateUnavailablePaymentErrorText(currencyCode)

    is PaymentUiError.Unexpected -> unexpectedPaymentErrorText(detail)
}
