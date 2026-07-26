package xyz.lilsus.raylsuite.feature.currencysettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings

interface CurrencyPreferences {
    val primaryCode: Flow<String>
    val secondaryCode: Flow<String>

    suspend fun currentPrimaryCode(): String

    suspend fun currentSecondaryCode(): String

    suspend fun setPrimaryCode(code: String)

    suspend fun setSecondaryCode(code: String)
}

class DefaultCurrencyPreferences(private val settings: Settings) : CurrencyPreferences {
    private val primaryState = MutableStateFlow(loadPrimaryCode())
    private val secondaryState = MutableStateFlow(loadSecondaryCode())

    override val primaryCode: Flow<String> = primaryState.asStateFlow()
    override val secondaryCode: Flow<String> = secondaryState.asStateFlow()

    override suspend fun currentPrimaryCode(): String = primaryState.value

    override suspend fun currentSecondaryCode(): String = secondaryState.value

    override suspend fun setPrimaryCode(code: String) {
        val normalised = CurrencyCatalog.infoFor(code).code
        if (normalised == primaryState.value) return

        settings.putString(KEY_DISPLAY_CURRENCY, normalised)
        primaryState.value = normalised
    }

    override suspend fun setSecondaryCode(code: String) {
        val normalised = CurrencyCatalog.infoFor(code).code
        if (normalised == secondaryState.value) return

        settings.putString(KEY_SECONDARY_DISPLAY_CURRENCY, normalised)
        secondaryState.value = normalised
    }

    private fun loadPrimaryCode(): String = normalisedStoredCode(
        key = KEY_DISPLAY_CURRENCY,
        fallback = CurrencyCatalog.DEFAULT_CODE
    )

    private fun loadSecondaryCode(): String = normalisedStoredCode(
        key = KEY_SECONDARY_DISPLAY_CURRENCY,
        fallback = CurrencyCatalog.DEFAULT_SECONDARY_CODE
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
        const val KEY_DISPLAY_CURRENCY = "display.currency.code"
        const val KEY_SECONDARY_DISPLAY_CURRENCY = "display.secondary.currency.code"
    }
}

@Composable
fun rememberCurrencyPreferences(storageName: String): CurrencyPreferences {
    val settings = rememberAppSettings(storageName)
    return remember(settings) {
        DefaultCurrencyPreferences(settings)
    }
}
