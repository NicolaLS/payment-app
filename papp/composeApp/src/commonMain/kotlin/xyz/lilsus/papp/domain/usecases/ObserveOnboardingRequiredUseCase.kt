package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import xyz.lilsus.papp.domain.repository.OnboardingRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/**
 * Determines whether the onboarding flow should be shown to the user.
 * Returns true only when the user has not completed onboarding and has no saved wallets.
 *
 * If a wallet exists but the completion flag is missing, the use case repairs the
 * onboarding state so older installs stop showing onboarding again.
 */
class ObserveOnboardingRequiredUseCase(
    private val onboardingRepository: OnboardingRepository,
    private val walletSettingsRepository: WalletSettingsRepository
) {
    operator fun invoke(): Flow<Boolean> = combine(
        onboardingRepository.hasCompletedOnboarding,
        walletSettingsRepository.wallets
    ) { hasCompleted, wallets ->
        val hasWallets = wallets.isNotEmpty()
        if (!hasCompleted && hasWallets) {
            onboardingRepository.markOnboardingCompleted()
            false
        } else {
            !hasCompleted
        }
    }.distinctUntilChanged()
}
