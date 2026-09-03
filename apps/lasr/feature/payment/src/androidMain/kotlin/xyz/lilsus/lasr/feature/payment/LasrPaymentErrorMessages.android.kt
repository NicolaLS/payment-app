package xyz.lilsus.lasr.feature.payment

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import xyz.lilsus.raylsuite.core.ui.resources.LocalizedTextKey
import xyz.lilsus.raylsuite.core.ui.resources.resolve
import xyz.lilsus.raylsuite.feature.paymentui.PaymentUiTextKey
import xyz.lilsus.raylsuite.feature.paymentui.androidStringResource

@Composable
fun lasrPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toLocalizedText().resolve(::lasrPaymentStringResource)

fun getLasrPaymentErrorMessageFor(error: PaymentUiError, context: Context): String =
    error.toLocalizedText().resolve(context, ::lasrPaymentStringResource)

@StringRes
private fun lasrPaymentStringResource(key: LocalizedTextKey): Int = when (key) {
    is LasrPaymentTextKey -> when (key) {
        LasrPaymentTextKey.ErrorMissingWalletConnection ->
            R.string.error_missing_wallet_connection

        LasrPaymentTextKey.ErrorNetworkUnavailable -> R.string.error_network_unavailable

        LasrPaymentTextKey.ErrorPaymentRejectedGeneric ->
            R.string.error_payment_rejected_generic

        LasrPaymentTextKey.ErrorPaymentRejectedMessage ->
            R.string.error_payment_rejected_message

        LasrPaymentTextKey.ErrorPaymentUnconfirmed -> R.string.error_payment_unconfirmed

        LasrPaymentTextKey.ErrorPaymentUnconfirmedMessage ->
            R.string.error_payment_unconfirmed_message

        LasrPaymentTextKey.ErrorRelayConnectionFailed ->
            R.string.error_relay_connection_failed
    }

    is PaymentUiTextKey -> key.androidStringResource()

    else -> error("Unknown Lasr payment text key: ${key.table}.${key.key}")
}
