package xyz.lilsus.papp.e2e

import android.content.Intent
import xyz.lilsus.papp.navigation.PaymentInputSource

private const val ARG_PAYMENT_INPUT = "e2ePaymentInput"
private const val ARG_PAYMENT_INPUT_SOURCE = "e2ePaymentInputSource"

data class E2ePaymentInput(val value: String, val source: PaymentInputSource)

fun Intent?.e2ePaymentInput(): E2ePaymentInput? {
    val extras = this?.extras ?: return null
    val value = extras.string(ARG_PAYMENT_INPUT)?.takeIf { it.isNotBlank() } ?: return null
    val source = when (extras.string(ARG_PAYMENT_INPUT_SOURCE)?.trim()?.lowercase()) {
        null,
        "",
        "deep_link" -> PaymentInputSource.DeepLink

        "camera" -> PaymentInputSource.Camera

        else -> error("Unknown E2E payment input source")
    }
    return E2ePaymentInput(value = value, source = source)
}

@Suppress("DEPRECATION")
private fun android.os.Bundle.string(key: String): String? = get(key)?.toString()
