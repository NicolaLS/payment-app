package xyz.lilsus.raylsuite.feature.paymenthub

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class PaymentHubLensPreferencesTest {
    private val dock = PaymentHubLensId("dock")
    private val launcher = PaymentHubLensId("launcher")

    @Test
    fun resolvesStoredThenDefaultThenFirst() {
        assertEquals(launcher, resolvePaymentHubLensId(launcher, listOf(dock, launcher)))
        assertEquals(dock, resolvePaymentHubLensId(PaymentHubLensId("gone"), listOf(dock, launcher)))
        assertEquals(dock, resolvePaymentHubLensId(null, listOf(launcher, dock)))
        assertEquals(launcher, resolvePaymentHubLensId(PaymentHubLensId("gone"), listOf(launcher)))
        assertNull(resolvePaymentHubLensId(dock, emptyList()))
    }

    @Test
    fun persistsSelectedLensAsString() = runTest {
        val settings = MapSettings()
        val preferences = DefaultPaymentHubLensPreferences(settings)
        assertNull(preferences.selectedLensId.value)

        preferences.select(launcher)

        assertEquals("launcher", settings.getStringOrNull("paymentHub.selectedLens"))
        assertEquals(launcher, DefaultPaymentHubLensPreferences(settings).selectedLensId.value)
    }
}
