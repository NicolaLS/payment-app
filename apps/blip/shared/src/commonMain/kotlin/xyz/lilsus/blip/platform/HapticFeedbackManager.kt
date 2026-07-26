package xyz.lilsus.blip.platform

interface HapticFeedbackManager {
    fun notifyScanSuccess()
    fun notifyPaymentSuccess()
}

expect fun createHapticFeedbackManager(): HapticFeedbackManager
