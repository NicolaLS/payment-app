package xyz.lilsus.blip.feature.payment

import androidx.compose.runtime.Composable
import xyz.lilsus.raylsuite.core.ui.resources.resolve

@Composable
fun blipPaymentErrorMessageFor(error: PaymentUiError): String = error.toLocalizedText().resolve()
