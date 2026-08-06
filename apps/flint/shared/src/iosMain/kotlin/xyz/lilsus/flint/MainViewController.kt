package xyz.lilsus.flint

import androidx.compose.ui.window.ComposeUIViewController
import xyz.lilsus.flint.application.payment.PaymentLinkInbox

@Suppress("FunctionName")
fun MainViewController(
    bootstrapConfig: AppBootstrapConfig,
    runtime: AppRuntime,
    paymentLinks: PaymentLinkInbox
) = ComposeUIViewController {
    App(
        bootstrapConfig = bootstrapConfig,
        runtime = runtime,
        paymentLinks = paymentLinks
    )
}
