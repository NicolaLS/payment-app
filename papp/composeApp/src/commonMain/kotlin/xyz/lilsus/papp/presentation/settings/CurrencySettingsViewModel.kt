package xyz.lilsus.papp.presentation.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.getString
import xyz.lilsus.papp.domain.model.CurrencyCatalog
import xyz.lilsus.papp.domain.use_cases.ObserveCurrencyPreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.SetCurrencyPreferenceUseCase

data class CurrencySettingsUiState(
    val selectedCode: String = CurrencyCatalog.DEFAULT_CODE,
    val searchQuery: String = "",
    val options: List<CurrencyOption> = emptyList(),
)

data class CurrencyOption(
    val code: String,
    val label: String,
)

class CurrencySettingsViewModel internal constructor(
    private val observeCurrency: ObserveCurrencyPreferenceUseCase,
    private val setCurrency: SetCurrencyPreferenceUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(CurrencySettingsUiState())
    val uiState: StateFlow<CurrencySettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            val options = loadOptions()
            _uiState.value = _uiState.value.copy(options = options)

            observeCurrency().collectLatest { currency ->
                val info = CurrencyCatalog.infoFor(currency)
                _uiState.value = _uiState.value.copy(selectedCode = info.code)
            }
        }
    }

    private suspend fun loadOptions(): List<CurrencyOption> = CurrencyCatalog.supportedCodes.map { code ->
        val info = CurrencyCatalog.infoFor(code)
        val label = getString(info.nameRes)
        CurrencyOption(code = info.code, label = label)
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectCurrency(code: String) {
        _uiState.value = _uiState.value.copy(selectedCode = code)
        scope.launch {
            setCurrency(code)
        }
    }

    fun clear() {
        scope.cancel()
    }
}
