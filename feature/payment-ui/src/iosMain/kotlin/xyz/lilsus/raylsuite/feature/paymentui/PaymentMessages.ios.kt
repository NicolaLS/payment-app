package xyz.lilsus.raylsuite.feature.paymentui

import xyz.lilsus.raylsuite.core.ui.resources.resolveNative

fun PaymentToastMessage.localizedMessage(): String = localizedText().resolveNative()
