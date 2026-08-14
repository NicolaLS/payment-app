package xyz.lilsus.flint.feature.payment

import androidx.compose.runtime.Composable
import xyz.lilsus.flint.feature.payment.generated.resources.Res
import xyz.lilsus.flint.feature.payment.generated.resources.error_invalid_invoice
import xyz.lilsus.flint.feature.payment.generated.resources.error_invalid_invoice_with_details
import xyz.lilsus.flint.feature.payment.generated.resources.error_lnurl
import xyz.lilsus.flint.feature.payment.generated.resources.error_lnurl_with_details
import xyz.lilsus.flint.feature.payment.generated.resources.error_missing_wallet_connection
import xyz.lilsus.flint.feature.payment.generated.resources.error_payment_rejected_generic
import xyz.lilsus.flint.feature.payment.generated.resources.error_payment_rejected_message
import xyz.lilsus.flint.feature.payment.generated.resources.error_payment_unconfirmed
import xyz.lilsus.flint.feature.payment.generated.resources.error_payment_unconfirmed_message
import xyz.lilsus.flint.feature.payment.generated.resources.error_relay_connection_failed
import xyz.lilsus.flint.feature.payment.generated.resources.error_unexpected_generic
import xyz.lilsus.flint.feature.payment.generated.resources.error_unexpected_with_details
import xyz.lilsus.raylsuite.core.ui.resources.LocalizedText
import xyz.lilsus.raylsuite.core.ui.resources.localizedTextWithOptionalDetail

@Composable
fun flintPaymentErrorMessageFor(error: PaymentUiError): String = error.toLocalizedText().resolve()

suspend fun getFlintPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toLocalizedText().resolveInCoroutine()

private fun PaymentUiError.toLocalizedText(): LocalizedText = when (this) {
    is PaymentUiError.Spark -> when (val sparkError = error) {
        SparkPaymentError.WalletUnavailable ->
            LocalizedText(Res.string.error_missing_wallet_connection)

        SparkPaymentError.SdkUnavailable ->
            LocalizedText(Res.string.error_relay_connection_failed)

        SparkPaymentError.StorageUnavailable,
        is SparkPaymentError.Unexpected ->
            localizedTextWithOptionalDetail(
                sparkError.detail,
                Res.string.error_unexpected_generic,
                Res.string.error_unexpected_with_details
            )

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

    is PaymentUiError.InvalidInvoice ->
        localizedTextWithOptionalDetail(
            reason,
            Res.string.error_invalid_invoice,
            Res.string.error_invalid_invoice_with_details
        )

    is PaymentUiError.Lnurl ->
        localizedTextWithOptionalDetail(
            reason,
            Res.string.error_lnurl,
            Res.string.error_lnurl_with_details
        )

    is PaymentUiError.Unexpected ->
        localizedTextWithOptionalDetail(
            detail,
            Res.string.error_unexpected_generic,
            Res.string.error_unexpected_with_details
        )
}
