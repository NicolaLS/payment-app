package xyz.lilsus.raylsuite.feature.currencysettings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog

interface CurrencyPreferences {
    val code: Flow<String>

    suspend fun currentCode(): String

    suspend fun setCode(code: String)
}

class DefaultCurrencyPreferences(private val settings: Settings) : CurrencyPreferences {
    private val state = MutableStateFlow(loadCode())

    override val code: Flow<String> = state.asStateFlow()

    override suspend fun currentCode(): String = state.value

    override suspend fun setCode(code: String) {
        val normalised = CurrencyCatalog.infoFor(code).code
        if (normalised == state.value) return

        settings.putString(KEY_CURRENCY, normalised)
        state.value = normalised
    }

    private fun loadCode(): String = normalisedStoredCode(
        key = KEY_CURRENCY,
        fallback = CurrencyCatalog.DEFAULT_CODE
    )

    private fun normalisedStoredCode(key: String, fallback: String): String {
        val stored =
            settings
                .getStringOrNull(key)
                ?.takeIf(String::isNotBlank)
                ?: fallback
        return CurrencyCatalog.infoFor(stored).code
    }

    private companion object {
        const val KEY_CURRENCY = "currency.code"
    }
}
