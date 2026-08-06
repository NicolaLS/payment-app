package xyz.lilsus.flint.feature.payment

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
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

@Composable
fun flintPaymentErrorMessageFor(error: PaymentUiError): String = error.toMessage().resolve()

suspend fun getFlintPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toMessage().resolveInCoroutine()

private fun PaymentUiError.toMessage(): LocalizedMessage = when (this) {
    is PaymentUiError.Spark -> when (val sparkError = error) {
        SparkPaymentError.WalletUnavailable ->
            LocalizedMessage(Res.string.error_missing_wallet_connection)

        SparkPaymentError.SdkUnavailable ->
            LocalizedMessage(Res.string.error_relay_connection_failed)

        SparkPaymentError.StorageUnavailable,
        is SparkPaymentError.Unexpected ->
            optionalDetailMessage(
                sparkError.detail,
                Res.string.error_unexpected_generic,
                Res.string.error_unexpected_with_details
            )

        SparkPaymentError.CapacityReached ->
            LocalizedMessage(Res.string.error_payment_rejected_generic)

        is SparkPaymentError.Rejected ->
            optionalDetailMessage(
                sparkError.detail,
                Res.string.error_payment_rejected_generic,
                Res.string.error_payment_rejected_message
            )

        is SparkPaymentError.OutcomeUnknown ->
            optionalDetailMessage(
                sparkError.detail,
                Res.string.error_payment_unconfirmed,
                Res.string.error_payment_unconfirmed_message
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
