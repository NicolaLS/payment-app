package xyz.lilsus.blip.data.settings

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import xyz.lilsus.blip.domain.model.CurrencyCatalog

class CurrencyPreferencesRepositoryImplTest {
    @Test
    fun defaultsPrimaryToSatsAndSecondaryToUsd() = runTest {
        val repository = CurrencyPreferencesRepositoryImpl(MapSettings())

        assertEquals(CurrencyCatalog.DEFAULT_CODE, repository.getCurrencyCode())
        assertEquals(
            CurrencyCatalog.DEFAULT_SECONDARY_CODE,
            repository.getSecondaryCurrencyCode()
        )
    }

    @Test
    fun storesPrimaryAndSecondaryIndependently() = runTest {
        val settings = MapSettings()
        val repository = CurrencyPreferencesRepositoryImpl(settings)

        repository.setCurrencyCode("EUR")
        repository.setSecondaryCurrencyCode("GBP")

        val restored = CurrencyPreferencesRepositoryImpl(settings)
        assertEquals("EUR", restored.getCurrencyCode())
        assertEquals("GBP", restored.getSecondaryCurrencyCode())
    }
}
