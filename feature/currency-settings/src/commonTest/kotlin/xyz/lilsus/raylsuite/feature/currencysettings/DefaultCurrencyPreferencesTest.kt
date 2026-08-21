package xyz.lilsus.raylsuite.feature.currencysettings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog

class DefaultCurrencyPreferencesTest {
    @Test
    fun storesSelectedCurrency() = runTest {
        val settings = MapSettings()
        val preferences = DefaultCurrencyPreferences(settings)

        assertEquals(CurrencyCatalog.DEFAULT_CODE, preferences.currentCode())

        preferences.setCode("eur")

        val restored = DefaultCurrencyPreferences(settings)
        assertEquals("EUR", restored.currentCode())
    }
}
