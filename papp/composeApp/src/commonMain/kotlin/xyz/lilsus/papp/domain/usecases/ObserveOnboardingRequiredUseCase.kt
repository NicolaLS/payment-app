package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.lilsus.papp.domain.repository.OnboardingRepository

/**
 * Determines whether the onboarding flow should be shown to the user.
 * Returns true if the user has not completed onboarding.
 *
 * Note: This uses regular (non-encrypted) settings storage, so uninstalling
 * the app will reset the onboarding state.
 */
class ObserveOnboardingRequiredUseCase(private val onboardingRepository: OnboardingRepository) {
    operator fun invoke(): Flow<Boolean> =
        onboardingRepository.hasCompletedOnboarding.map { hasCompleted -> !hasCompleted }
}
