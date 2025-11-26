package xyz.lilsus.papp.domain.model

/**
 * Controls how payment confirmations should be presented to the user.
 */
enum class PaymentConfirmationMode {
    Always,
    Above
}

data class PaymentPreferences(
    val confirmationMode: PaymentConfirmationMode = PaymentConfirmationMode.Above,
    val thresholdSats: Long = DEFAULT_CONFIRMATION_THRESHOLD_SATS,
    val confirmManualEntry: Boolean = false,
    val vibrateOnScan: Boolean = true,
    val vibrateOnPayment: Boolean = true
) {
    fun normalise(): PaymentPreferences {
        val normalisedThreshold = thresholdSats
            .coerceIn(MIN_CONFIRMATION_THRESHOLD_SATS, MAX_CONFIRMATION_THRESHOLD_SATS)
        return copy(thresholdSats = normalisedThreshold)
    }

    companion object {
        const val DEFAULT_CONFIRMATION_THRESHOLD_SATS = 100_000L
        const val MIN_CONFIRMATION_THRESHOLD_SATS = 10_000L
        const val MAX_CONFIRMATION_THRESHOLD_SATS = 1_000_000L
    }
}
