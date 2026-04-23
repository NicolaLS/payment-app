package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.papp.domain.repository.OnboardingRepository

private const val KEY_COMPLETED_ONBOARDING = "onboarding.completed"

/**
 * Creates regular (non-encrypted) Settings for onboarding.
 * The underlying store is scoped to the installed app variant so local debug installs can keep
 * onboarding state separate from release installs.
 */
expect fun createOnboardingSettings(): Settings

class OnboardingRepositoryImpl(private val settings: Settings) : OnboardingRepository {

    private val _hasCompletedOnboarding = MutableStateFlow(
        settings.getBoolean(KEY_COMPLETED_ONBOARDING, false)
    )

    override val hasCompletedOnboarding: Flow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    override suspend fun markOnboardingCompleted() {
        if (_hasCompletedOnboarding.value) return
        settings.putBoolean(KEY_COMPLETED_ONBOARDING, true)
        _hasCompletedOnboarding.value = true
    }
}
