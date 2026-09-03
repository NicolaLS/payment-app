package xyz.lilsus.lasr.feature.payment

import androidx.compose.runtime.Composable
import xyz.lilsus.raylsuite.core.ui.resources.resolve

@Composable
fun lasrPaymentErrorMessageFor(error: PaymentUiError): String = error.toLocalizedText().resolve()
