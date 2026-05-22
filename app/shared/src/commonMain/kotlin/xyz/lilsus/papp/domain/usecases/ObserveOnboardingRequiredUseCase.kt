package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import xyz.lilsus.papp.domain.repository.OnboardingRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/**
 * Determines whether the onboarding flow should be shown to the user.
 * Returns true when the user has not completed onboarding.
 *
 * If a wallet already existed when observation started but the completion flag is missing,
 * the use case repairs the onboarding state so older installs stop showing onboarding again.
 */
class ObserveOnboardingRequiredUseCase(
    private val onboardingRepository: OnboardingRepository,
    private val walletSettingsRepository: WalletSettingsRepository
) {
    operator fun invoke(): Flow<Boolean> = flow {
        val hadWalletsWhenObserved = walletSettingsRepository.getWallets().isNotEmpty()

        combine(
            onboardingRepository.hasCompletedOnboarding,
            walletSettingsRepository.wallets
        ) { hasCompleted, wallets ->
            val hasWallets = wallets.isNotEmpty()
            if (!hasCompleted && hasWallets && hadWalletsWhenObserved) {
                onboardingRepository.markOnboardingCompleted()
                false
            } else {
                !hasCompleted
            }
        }.distinctUntilChanged().collect(::emit)
    }.distinctUntilChanged()
}
