package xyz.lilsus.raylsuite.feature.currencysettings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog

class DefaultCurrencyPreferencesTest {
    @Test
    fun storesPrimaryAndSecondaryIndependently() = runTest {
        val settings = MapSettings()
        val preferences = DefaultCurrencyPreferences(settings)

        assertEquals(CurrencyCatalog.DEFAULT_CODE, preferences.currentPrimaryCode())
        assertEquals(
            CurrencyCatalog.DEFAULT_SECONDARY_CODE,
            preferences.currentSecondaryCode()
        )

        preferences.setPrimaryCode("eur")
        preferences.setSecondaryCode("gbp")

        val restored = DefaultCurrencyPreferences(settings)
        assertEquals("EUR", restored.currentPrimaryCode())
        assertEquals("GBP", restored.currentSecondaryCode())
    }
}
