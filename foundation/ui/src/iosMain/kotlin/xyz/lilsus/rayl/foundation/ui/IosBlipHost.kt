package xyz.lilsus.rayl.foundation.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController
import xyz.lilsus.rayl.blip.platform.IosBlipRuntime

class IosBlipHost {
    private val runtime = IosBlipRuntime()
    private var incomingPaymentUri by mutableStateOf<String?>(null)

    fun viewController(): UIViewController = ComposeUIViewController {
        BlipApp(
            runtime = runtime,
            incomingPaymentUri = incomingPaymentUri,
            onIncomingPaymentUriConsumed = { incomingPaymentUri = null },
            appVersionName = NSBundle.mainBundle
                .infoDictionary
                ?.get("CFBundleShortVersionString") as? String ?: "?"
        )
    }

    fun openPaymentUri(uri: String) {
        incomingPaymentUri = uri.takeIf(String::isNotBlank)
    }
}
