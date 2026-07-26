package xyz.lilsus.raylsuite.feature.currencysettings

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
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog

data class CurrencySettingsUiState(
    val selectedPrimaryCode: String = CurrencyCatalog.DEFAULT_CODE,
    val selectedSecondaryCode: String = CurrencyCatalog.DEFAULT_SECONDARY_CODE,
    val activePreference: CurrencyPreference = CurrencyPreference.Primary,
    val searchQuery: String = ""
) {
    val selectedCode: String
        get() =
            when (activePreference) {
                CurrencyPreference.Primary -> selectedPrimaryCode
                CurrencyPreference.Secondary -> selectedSecondaryCode
            }
}

enum class CurrencyPreference {
    Primary,
    Secondary
}

class CurrencySettingsViewModel(
    private val preferences: CurrencyPreferences,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableUiState = MutableStateFlow(CurrencySettingsUiState())

    val uiState: StateFlow<CurrencySettingsUiState> = mutableUiState.asStateFlow()

    init {
        scope.launch {
            preferences.primaryCode.collectLatest { code ->
                mutableUiState.value = mutableUiState.value.copy(selectedPrimaryCode = code)
            }
        }
        scope.launch {
            preferences.secondaryCode.collectLatest { code ->
                mutableUiState.value = mutableUiState.value.copy(selectedSecondaryCode = code)
            }
        }
    }

    fun updateSearch(query: String) {
        mutableUiState.value = mutableUiState.value.copy(searchQuery = query)
    }

    fun selectPreference(preference: CurrencyPreference) {
        mutableUiState.value = mutableUiState.value.copy(activePreference = preference)
    }

    fun selectCurrency(code: String) {
        val normalisedCode = CurrencyCatalog.infoFor(code).code
        when (mutableUiState.value.activePreference) {
            CurrencyPreference.Primary -> {
                mutableUiState.value =
                    mutableUiState.value.copy(selectedPrimaryCode = normalisedCode)
                scope.launch {
                    preferences.setPrimaryCode(normalisedCode)
                }
            }

            CurrencyPreference.Secondary -> {
                mutableUiState.value =
                    mutableUiState.value.copy(selectedSecondaryCode = normalisedCode)
                scope.launch {
                    preferences.setSecondaryCode(normalisedCode)
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
    }
}
