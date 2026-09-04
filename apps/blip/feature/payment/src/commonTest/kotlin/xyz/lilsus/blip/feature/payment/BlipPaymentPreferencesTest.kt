package xyz.lilsus.blip.feature.payment

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlipPaymentPreferencesTest {
    @Test
    fun confirmFeeRemainsAnIndependentBlipPreference() {
        val settings = MapSettings()
        val preferences = BlipPaymentPreferences(settings)

        assertFalse(preferences.confirmFee())
        preferences.setConfirmFee(true)

        assertTrue(BlipPaymentPreferences(settings).confirmFee())
    }
}
