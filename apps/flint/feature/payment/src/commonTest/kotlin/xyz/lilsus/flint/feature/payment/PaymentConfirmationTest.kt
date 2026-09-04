package xyz.lilsus.flint.feature.payment

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaymentConfirmationTest {
    @Test
    fun presetTargetRequiresConfirmationWhenEngineAllowsAutoPay() {
        assertTrue(
            requiresPreparedPaymentConfirmation(
                engineRequiresConfirmation = false,
                isPresetTarget = true
            )
        )
    }

    @Test
    fun enginePolicyStillControlsNonPresetPayment() {
        assertFalse(
            requiresPreparedPaymentConfirmation(
                engineRequiresConfirmation = false,
                isPresetTarget = false
            )
        )
        assertTrue(
            requiresPreparedPaymentConfirmation(
                engineRequiresConfirmation = true,
                isPresetTarget = false
            )
        )
    }
}
