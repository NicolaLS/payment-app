package xyz.lilsus.raylsuite.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentPreferencesTest {
    @Test
    fun normaliseClampsThresholdToSupportedRange() {
        assertEquals(
            PaymentPreferences.MIN_CONFIRMATION_THRESHOLD_SATS,
            PaymentPreferences(thresholdSats = 1).normalise().thresholdSats
        )
        assertEquals(
            PaymentPreferences.MAX_CONFIRMATION_THRESHOLD_SATS,
            PaymentPreferences(thresholdSats = Long.MAX_VALUE).normalise().thresholdSats
        )
    }

    @Test
    fun thresholdIndexUsesFirstStepAtOrAboveValue() {
        assertEquals(0, PaymentPreferences.thresholdToStepIndex(1))
        assertEquals(4, PaymentPreferences.thresholdToStepIndex(10_000))
        assertEquals(5, PaymentPreferences.thresholdToStepIndex(10_001))
        assertEquals(7, PaymentPreferences.thresholdToStepIndex(Long.MAX_VALUE))
    }
}
