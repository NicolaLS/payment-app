package xyz.lilsus.raylsuite.feature.paymentsettings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PaymentPreferencesRepositoryTest {
    @Test
    fun mutationPersistsOnlyTheChangedPreference() = runTest {
        val settings = MapSettings()
        val repository = DefaultPaymentPreferencesRepository(settings)

        repository.setVibrateOnScan(false)

        assertEquals(setOf("payments.vibrateOnScan"), settings.keys)
    }
}
