package xyz.lilsus.raylsuite.feature.languagesettings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.model.LanguagePreference

class LanguageSettingsViewModelTest {
    @Test
    fun selectingDeviceLanguageClearsOverride() = runTest {
        val repository = FakeLanguageRepository()
        val viewModel =
            LanguageSettingsViewModel(
                repository = repository,
                dispatcher = StandardTestDispatcher(testScheduler)
            )

        testScheduler.advanceUntilIdle()
        viewModel.selectOption("de")
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.clearOverrideCalls)
        viewModel.clear()
    }
}

private class FakeLanguageRepository : LanguageRepository {
    private val mutablePreference =
        MutableStateFlow<LanguagePreference>(
            LanguagePreference.Override(
                overrideTag = "en",
                resolvedTag = "en",
                deviceTag = "de"
            )
        )

    override val preference: StateFlow<LanguagePreference> = mutablePreference

    var clearOverrideCalls = 0

    override suspend fun setLanguage(tag: String) = Unit

    override suspend fun clearOverride() {
        clearOverrideCalls += 1
    }

    override suspend fun refresh() = Unit

    override fun close() = Unit
}
