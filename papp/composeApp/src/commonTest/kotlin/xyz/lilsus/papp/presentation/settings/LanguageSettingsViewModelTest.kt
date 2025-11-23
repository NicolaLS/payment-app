package xyz.lilsus.papp.presentation.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.domain.model.LanguagePreference
import xyz.lilsus.papp.domain.repository.LanguageRepository
import xyz.lilsus.papp.domain.use_cases.ClearLanguageOverrideUseCase
import xyz.lilsus.papp.domain.use_cases.ObserveLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.RefreshLanguagePreferenceUseCase
import xyz.lilsus.papp.domain.use_cases.SetLanguagePreferenceUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageSettingsViewModelTest {

    @Test
    fun selectsSystemByDefault() = runTest {
        val repository = FakeLanguageRepository(deviceTag = "en")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LanguageSettingsViewModel(
            observeLanguage = ObserveLanguagePreferenceUseCase(repository),
            setLanguage = SetLanguagePreferenceUseCase(repository),
            clearOverride = ClearLanguageOverrideUseCase(repository),
            refreshLanguage = RefreshLanguagePreferenceUseCase(repository),
            dispatcher = dispatcher,
            languageLabelProvider = { info -> "label:${info.code}" },
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("en", state.selectedCode)
        assertEquals("en", state.deviceCode)
        assertTrue(state.options.any { it.id == "en" })
        viewModel.clear()
    }

    @Test
    fun selectingLanguageSetsOverride() = runTest {
        val repository = FakeLanguageRepository(deviceTag = "de")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LanguageSettingsViewModel(
            observeLanguage = ObserveLanguagePreferenceUseCase(repository),
            setLanguage = SetLanguagePreferenceUseCase(repository),
            clearOverride = ClearLanguageOverrideUseCase(repository),
            refreshLanguage = RefreshLanguagePreferenceUseCase(repository),
            dispatcher = dispatcher,
            languageLabelProvider = { info -> "label:${info.code}" },
        )

        advanceUntilIdle()
        viewModel.selectOption("en")
        advanceUntilIdle()

        assertEquals(listOf("en"), repository.setCalls)
        val state = viewModel.uiState.value
        assertEquals("en", state.selectedCode)
        assertEquals("de", state.deviceCode)
        assertTrue(state.options.any { it.id == "en" })
        viewModel.clear()
    }

    @Test
    fun selectingSystemClearsOverride() = runTest {
        val repository = FakeLanguageRepository(deviceTag = "de")
        repository.state.value = LanguagePreference.Override("en", "en", deviceTag = "de")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = LanguageSettingsViewModel(
            observeLanguage = ObserveLanguagePreferenceUseCase(repository),
            setLanguage = SetLanguagePreferenceUseCase(repository),
            clearOverride = ClearLanguageOverrideUseCase(repository),
            refreshLanguage = RefreshLanguagePreferenceUseCase(repository),
            dispatcher = dispatcher,
            languageLabelProvider = { info -> "label:${info.code}" },
        )

        advanceUntilIdle()
        viewModel.selectOption("de")
        advanceUntilIdle()

        assertEquals(1, repository.clearCalls)
        val state = viewModel.uiState.value
        assertEquals("de", state.selectedCode)
        assertEquals("de", state.deviceCode)
        viewModel.clear()
    }
}

private class FakeLanguageRepository(
    private val deviceTag: String,
) : LanguageRepository {
    val state = MutableStateFlow<LanguagePreference>(
        LanguagePreference.System(resolvedTag = deviceTag),
    )

    val setCalls = mutableListOf<String>()
    var clearCalls = 0

    override val preference: StateFlow<LanguagePreference> get() = state

    override suspend fun setLanguage(tag: String) {
        setCalls += tag
        state.value = LanguagePreference.Override(tag, tag, deviceTag)
    }

    override suspend fun clearOverride() {
        clearCalls += 1
        state.value = LanguagePreference.System(deviceTag)
    }

    override suspend fun refresh() {
        // no-op for tests
    }
}
