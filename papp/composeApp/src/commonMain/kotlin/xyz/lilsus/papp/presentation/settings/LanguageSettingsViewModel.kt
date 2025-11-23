package xyz.lilsus.papp.presentation.settings

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import xyz.lilsus.papp.domain.model.LanguageCatalog
import xyz.lilsus.papp.domain.model.LanguageInfo
import xyz.lilsus.papp.domain.model.LanguagePreference
import xyz.lilsus.papp.domain.use_cases.ClearLanguageOverrideUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.RefreshLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.SetLanguagePreferenceUseCase

data class LanguageSettingsUiState(
    val searchQuery: String = "",
    val selectedCode: String = "",
    val deviceCode: String = "",
    val options: List<LanguageOption> = emptyList(),
)

data class LanguageOption(
    val id: String,
    val title: String,
    val tag: String?,
)

class LanguageSettingsViewModel internal constructor(
    private val observeLanguage: ObserveLanguagePreferenceUseCase,
    private val setLanguage: SetLanguagePreferenceUseCase,
    private val clearOverride: ClearLanguageOverrideUseCase,
    private val refreshLanguage: RefreshLanguagePreferenceUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val languageLabelProvider: suspend (LanguageInfo) -> String = { info -> info.displayName },
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _uiState = MutableStateFlow(LanguageSettingsUiState())
    val uiState: StateFlow<LanguageSettingsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            refreshLanguage()

            observeLanguage().collectLatest { preference ->
                val options = loadLanguageOptions()
                val deviceCode = normaliseCode(preference.deviceTag)
                val selectedCode = when (preference) {
                    is LanguagePreference.System -> deviceCode
                    is LanguagePreference.Override -> normaliseCode(preference.overrideTag)
                }
                _uiState.value = _uiState.value.copy(
                    options = options,
                    selectedCode = selectedCode,
                    deviceCode = deviceCode,
                )
            }
        }
    }

    private suspend fun loadLanguageOptions(): List<LanguageOption> = LanguageCatalog.supported.map { info ->
        val label = languageLabelProvider(info)
        LanguageOption(
            id = info.code,
            title = label,
            tag = info.tag,
        )
    }

    private fun normaliseCode(tag: String): String {
        LanguageCatalog.infoForTag(tag)?.let { return it.code }
        val languageCode = tag.substringBefore('-')
        return LanguageCatalog.infoForCode(languageCode)?.code ?: LanguageCatalog.fallback.code
    }

    fun updateSearch(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun selectOption(optionId: String) {
        scope.launch {
            val deviceCode = _uiState.value.deviceCode
            if (optionId.equals(deviceCode, ignoreCase = true)) {
                clearOverride()
            } else {
                val tag = LanguageCatalog.infoForCode(optionId)?.tag ?: optionId
                setLanguage(tag)
            }
        }
    }

    fun clear() {
        scope.cancel()
    }
}
