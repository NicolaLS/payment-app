package xyz.lilsus.papp.presentation.settings

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
import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.domain.usecases.ObserveThemePreferenceUseCase
import xyz.lilsus.papp.domain.usecases.SetThemePreferenceUseCase

data class ThemeSettingsUiState(val selected: ThemePreference = ThemePreference.System)

class ThemeSettingsViewModel internal constructor(
    private val observeTheme: ObserveThemePreferenceUseCase,
    private val setTheme: SetThemePreferenceUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(ThemeSettingsUiState())
    val uiState: StateFlow<ThemeSettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            observeTheme().collectLatest { preference ->
                _uiState.value = _uiState.value.copy(selected = preference)
            }
        }
    }

    fun selectTheme(preference: ThemePreference) {
        if (preference == _uiState.value.selected) return
        _uiState.value = _uiState.value.copy(selected = preference)
        scope.launch {
            setTheme(preference)
        }
    }

    fun clear() {
        scope.cancel()
    }
}
