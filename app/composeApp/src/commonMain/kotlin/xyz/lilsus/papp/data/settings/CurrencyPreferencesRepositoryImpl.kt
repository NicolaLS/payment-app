package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.repository.CurrencyPreferencesRepository

private const val KEY_DISPLAY_CURRENCY = "display.currency.code"

class CurrencyPreferencesRepositoryImpl(private val settings: Settings) :
    CurrencyPreferencesRepository {

    private val state = MutableStateFlow(loadCurrencyCode())

    override val currencyCode: Flow<String> = state.asStateFlow()

    override suspend fun getCurrencyCode(): String = state.value

    override suspend fun setCurrencyCode(code: String) {
        val normalised = CurrencyCatalog.infoFor(code).code
        if (normalised == state.value) return
        settings.putString(KEY_DISPLAY_CURRENCY, normalised)
        state.value = normalised
    }

    private fun loadCurrencyCode(): String {
        val stored = settings.getStringOrNull(KEY_DISPLAY_CURRENCY)
            ?.takeIf { it.isNotBlank() }
            ?: CurrencyCatalog.DEFAULT_CODE
        return CurrencyCatalog.infoFor(stored).code
    }
}
