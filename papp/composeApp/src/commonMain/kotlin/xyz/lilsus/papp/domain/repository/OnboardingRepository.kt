package xyz.lilsus.papp.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting onboarding completion state.
 * Uses regular app settings rather than secure wallet storage.
 */
interface OnboardingRepository {
    /**
     * Emits whether the user has completed onboarding.
     * This is stored in regular app settings rather than secure wallet storage.
     */
    val hasCompletedOnboarding: Flow<Boolean>

    /**
     * Marks onboarding as completed.
     * This should be called when a wallet is successfully connected.
     */
    suspend fun markOnboardingCompleted()
}
