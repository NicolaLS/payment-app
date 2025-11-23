package xyz.lilsus.papp.platform

interface HapticFeedbackManager {
    fun notifyScanSuccess()
    fun notifyPaymentSuccess()
}

expect fun createHapticFeedbackManager(): HapticFeedbackManager
