package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.repository.CurrencyPreferencesRepository

private const val KEY_DISPLAY_CURRENCY = "display.currency.code"
private const val KEY_SECONDARY_DISPLAY_CURRENCY = "display.secondary.currency.code"

class CurrencyPreferencesRepositoryImpl(private val settings: Settings) :
    CurrencyPreferencesRepository {

    private val state = MutableStateFlow(loadCurrencyCode())
    private val secondaryState = MutableStateFlow(loadSecondaryCurrencyCode())

    override val currencyCode: Flow<String> = state.asStateFlow()
    override val secondaryCurrencyCode: Flow<String> = secondaryState.asStateFlow()

    override suspend fun getCurrencyCode(): String = state.value
    override suspend fun getSecondaryCurrencyCode(): String = secondaryState.value

    override suspend fun setCurrencyCode(code: String) {
        val normalised = CurrencyCatalog.infoFor(code).code
        if (normalised == state.value) return
        settings.putString(KEY_DISPLAY_CURRENCY, normalised)
        state.value = normalised
    }

    override suspend fun setSecondaryCurrencyCode(code: String) {
        val normalised = CurrencyCatalog.infoFor(code).code
        if (normalised == secondaryState.value) return
        settings.putString(KEY_SECONDARY_DISPLAY_CURRENCY, normalised)
        secondaryState.value = normalised
    }

    private fun loadCurrencyCode(): String {
        val stored = settings.getStringOrNull(KEY_DISPLAY_CURRENCY)
            ?.takeIf { it.isNotBlank() }
            ?: CurrencyCatalog.DEFAULT_CODE
        return CurrencyCatalog.infoFor(stored).code
    }

    private fun loadSecondaryCurrencyCode(): String {
        val stored = settings.getStringOrNull(KEY_SECONDARY_DISPLAY_CURRENCY)
            ?.takeIf { it.isNotBlank() }
            ?: CurrencyCatalog.DEFAULT_SECONDARY_CODE
        return CurrencyCatalog.infoFor(stored).code
    }
}
