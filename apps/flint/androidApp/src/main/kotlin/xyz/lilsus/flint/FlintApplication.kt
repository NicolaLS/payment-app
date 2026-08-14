package xyz.lilsus.flint

import android.app.Application
import xyz.lilsus.flint.application.payment.PaymentLinkInbox
import xyz.lilsus.flint.application.payment.createPaymentLinkInbox

class FlintApplication : Application() {
    val paymentLinks: PaymentLinkInbox by lazy(::createPaymentLinkInbox)

    val runtime: AppRuntime by lazy {
        createAndroidAppRuntime(this, androidBootstrapConfig())
    }
}
