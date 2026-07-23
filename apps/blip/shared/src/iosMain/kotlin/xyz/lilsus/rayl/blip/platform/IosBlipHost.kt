package xyz.lilsus.rayl.blip.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import xyz.lilsus.rayl.blip.presentation.BlipApp

class IosBlipHost {
    private val runtime = IosBlipRuntime()
    private var incomingPaymentUri by mutableStateOf<String?>(null)

    fun viewController(): UIViewController = ComposeUIViewController {
        BlipApp(
            runtime = runtime,
            incomingPaymentUri = incomingPaymentUri,
            onIncomingPaymentUriConsumed = { incomingPaymentUri = null }
        )
    }

    fun openPaymentUri(uri: String) {
        incomingPaymentUri = uri.takeIf(String::isNotBlank)
    }
}
