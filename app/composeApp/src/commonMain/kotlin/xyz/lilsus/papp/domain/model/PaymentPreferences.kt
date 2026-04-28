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
        const val DEFAULT_CONFIRMATION_THRESHOLD_SATS = 10_000L
        const val MIN_CONFIRMATION_THRESHOLD_SATS = 500L
        const val MAX_CONFIRMATION_THRESHOLD_SATS = 100_000L

        /**
         * Discrete threshold steps for slider UX.
         * Uses approximate doubling with round numbers for intuitive selection.
         */
        val THRESHOLD_STEPS = listOf(
            500L,
            1_000L,
            2_000L,
            5_000L,
            10_000L,
            20_000L,
            50_000L,
            100_000L
        )

        /**
         * Finds the closest threshold step index for a given value.
         */
        fun thresholdToStepIndex(threshold: Long): Int {
            val index = THRESHOLD_STEPS.indexOfFirst { it >= threshold }
            return if (index < 0) THRESHOLD_STEPS.size - 1 else index
        }
    }
}
