package xyz.lilsus.papp.presentation.common

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.domain.model.AppError
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.error_invalid_wallet_uri
import papp.composeapp.generated.resources.error_missing_wallet_connection
import papp.composeapp.generated.resources.error_network_unavailable
import papp.composeapp.generated.resources.error_payment_rejected_code
import papp.composeapp.generated.resources.error_payment_rejected_full
import papp.composeapp.generated.resources.error_payment_rejected_generic
import papp.composeapp.generated.resources.error_payment_rejected_message
import papp.composeapp.generated.resources.error_timeout
import papp.composeapp.generated.resources.error_unexpected_generic
import papp.composeapp.generated.resources.error_unexpected_with_details

/**
 * Resolves a human-readable message for the provided [AppError] using localized string resources.
 */
@Composable
fun errorMessageFor(error: AppError): String = when (error) {
    AppError.MissingWalletConnection -> stringResource(Res.string.error_missing_wallet_connection)
    is AppError.PaymentRejected -> {
        val code = error.code?.takeUnless { it.isBlank() }
        val message = error.message?.takeUnless { it.isBlank() }
        when {
            code != null && message != null -> stringResource(
                Res.string.error_payment_rejected_full,
                code,
                message
            )

            code != null -> stringResource(Res.string.error_payment_rejected_code, code)
            message != null -> stringResource(Res.string.error_payment_rejected_message, message)
            else -> stringResource(Res.string.error_payment_rejected_generic)
        }
    }

    AppError.NetworkUnavailable -> stringResource(Res.string.error_network_unavailable)
    AppError.Timeout -> stringResource(Res.string.error_timeout)
    is AppError.InvalidWalletUri -> stringResource(
        Res.string.error_invalid_wallet_uri
    )
    is AppError.Unexpected -> {
        val details = error.message?.takeUnless { it.isBlank() }
        if (details != null) {
            stringResource(Res.string.error_unexpected_with_details, details)
        } else {
            stringResource(Res.string.error_unexpected_generic)
        }
    }
}
