package xyz.lilsus.blip.presentation.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import xyz.lilsus.blip.domain.model.CurrencyCatalog
import xyz.lilsus.blip.domain.usecases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.ObserveSecondaryCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.SetCurrencyPreferenceUseCase
import xyz.lilsus.blip.domain.usecases.SetSecondaryCurrencyPreferenceUseCase

data class CurrencySettingsUiState(
    val selectedPrimaryCode: String = CurrencyCatalog.DEFAULT_CODE,
    val selectedSecondaryCode: String = CurrencyCatalog.DEFAULT_SECONDARY_CODE,
    val activePreference: CurrencyPreference = CurrencyPreference.Primary,
    val searchQuery: String = "",
    val options: List<CurrencyOption> = emptyList()
) {
    val selectedCode: String
        get() = when (activePreference) {
            CurrencyPreference.Primary -> selectedPrimaryCode
            CurrencyPreference.Secondary -> selectedSecondaryCode
        }
}

enum class CurrencyPreference {
    Primary,
    Secondary
}

data class CurrencyOption(val code: String, val label: String)

class CurrencySettingsViewModel internal constructor(
    private val observeCurrency: ObserveCurrencyPreferenceUseCase,
    private val observeSecondaryCurrency: ObserveSecondaryCurrencyPreferenceUseCase,
    private val setCurrency: SetCurrencyPreferenceUseCase,
    private val setSecondaryCurrency: SetSecondaryCurrencyPreferenceUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(CurrencySettingsUiState())
    val uiState: StateFlow<CurrencySettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            _uiState.value = _uiState.value.copy(options = loadOptions())
        }

        scope.launch {
            observeCurrency().collectLatest { currency ->
                val info = CurrencyCatalog.infoFor(currency)
                _uiState.value = _uiState.value.copy(selectedPrimaryCode = info.code)
            }
        }

        scope.launch {
            observeSecondaryCurrency().collectLatest { currency ->
                val info = CurrencyCatalog.infoFor(currency)
                _uiState.value = _uiState.value.copy(selectedSecondaryCode = info.code)
            }
        }
    }

    private suspend fun loadOptions(): List<CurrencyOption> =
        CurrencyCatalog.supportedCodes.map { code ->
            val info = CurrencyCatalog.infoFor(code)
            val label = getString(info.nameRes)
            CurrencyOption(code = info.code, label = label)
        }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectPreference(preference: CurrencyPreference) {
        _uiState.value = _uiState.value.copy(activePreference = preference)
    }

    fun selectCurrency(code: String) {
        val info = CurrencyCatalog.infoFor(code)
        when (_uiState.value.activePreference) {
            CurrencyPreference.Primary -> {
                _uiState.value = _uiState.value.copy(selectedPrimaryCode = info.code)
                scope.launch {
                    setCurrency(info.code)
                }
            }

            CurrencyPreference.Secondary -> {
                _uiState.value = _uiState.value.copy(selectedSecondaryCode = info.code)
                scope.launch {
                    setSecondaryCurrency(info.code)
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
    }
}
