package xyz.lilsus.lasr.feature.payment

import xyz.lilsus.raylsuite.core.ui.resources.resolveNative

fun getLasrPaymentErrorMessageFor(error: PaymentUiError): String =
    error.toLocalizedText().resolveNative()
