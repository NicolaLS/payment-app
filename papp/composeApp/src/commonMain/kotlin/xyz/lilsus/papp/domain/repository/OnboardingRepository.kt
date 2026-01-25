package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting onboarding completion state.
 * Uses regular (non-encrypted) storage so that uninstalling the app
 * resets the onboarding state, making it easy for testers to test onboarding.
 */
interface OnboardingRepository {
    /**
     * Emits whether the user has completed onboarding.
     * This is stored in regular settings (not Keychain) so it resets on app uninstall.
     */
    val hasCompletedOnboarding: Flow<Boolean>

    /**
     * Marks onboarding as completed.
     * This should be called when a wallet is successfully connected.
     */
    suspend fun markOnboardingCompleted()
}
