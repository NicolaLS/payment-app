package xyz.lilsus.raylsuite.core.ui.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time

private class IosHapticFeedbackManager : HapticFeedbackManager {
    override fun notifyScanSuccess() {
        UIImpactFeedbackGenerator(
            style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
        ).apply {
            prepare()
            impactOccurred()
        }
    }

    override fun notifyPaymentSuccess() {
        val generator =
            UIImpactFeedbackGenerator(
                style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
            )
        generator.prepare()
        generator.impactOccurred()

        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, 30_000_000),
            dispatch_get_main_queue()
        ) {
            generator.prepare()
            generator.impactOccurred()
        }
    }
}

fun createHapticFeedbackManager(): HapticFeedbackManager = IosHapticFeedbackManager()
