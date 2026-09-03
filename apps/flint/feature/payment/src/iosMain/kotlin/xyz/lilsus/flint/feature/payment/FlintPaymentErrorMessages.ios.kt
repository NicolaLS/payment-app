package xyz.lilsus.flint.feature.payment

import xyz.lilsus.raylsuite.core.ui.resources.resolveNative

fun getFlintPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toLocalizedText().resolveNative()
