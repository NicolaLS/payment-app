package xyz.lilsus.flint.feature.payment

import androidx.compose.runtime.Composable
import xyz.lilsus.raylsuite.core.ui.resources.resolve

@Composable
fun flintPaymentErrorMessageFor(error: PaymentUiError): String = error.toLocalizedText().resolve()
