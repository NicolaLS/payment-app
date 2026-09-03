package xyz.lilsus.blip.feature.payment

import xyz.lilsus.raylsuite.core.ui.resources.resolveNative

fun getBlipPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toLocalizedText().resolveNative()
