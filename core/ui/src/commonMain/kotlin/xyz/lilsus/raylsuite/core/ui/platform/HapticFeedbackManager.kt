package xyz.lilsus.raylsuite.core.ui.platform

import androidx.compose.runtime.Composable

interface HapticFeedbackManager {
    fun notifyScanSuccess()

    fun notifyPaymentSuccess()
}

@Composable
expect fun rememberHapticFeedbackManager(): HapticFeedbackManager
