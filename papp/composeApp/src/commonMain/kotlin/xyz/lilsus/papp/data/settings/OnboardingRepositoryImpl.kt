package xyz.lilsus.papp.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.lilsus.papp.domain.repository.OnboardingRepository

private const val KEY_COMPLETED_ONBOARDING = "onboarding.completed"

/**
 * Creates regular (non-encrypted) Settings for onboarding.
 * On Android, this data is deleted when the app is uninstalled.
 * On iOS, NSUserDefaults may persist after uninstall, but wallet
 * credentials (stored in Keychain) also persist, so the behavior
 * is consistent: testers need to clear app data to reset onboarding.
 */
expect fun createOnboardingSettings(): Settings

class OnboardingRepositoryImpl(
    private val settings: Settings
) : OnboardingRepository {

    private val _completedOnboarding = MutableStateFlow(
        settings.getBoolean(KEY_COMPLETED_ONBOARDING, false)
    )

    override val hasCompletedOnboarding: Flow<Boolean> = _completedOnboarding.asStateFlow()

    override suspend fun markOnboardingCompleted() {
        if (_completedOnboarding.value) return
        settings.putBoolean(KEY_COMPLETED_ONBOARDING, true)
        _completedOnboarding.value = true
    }
}
