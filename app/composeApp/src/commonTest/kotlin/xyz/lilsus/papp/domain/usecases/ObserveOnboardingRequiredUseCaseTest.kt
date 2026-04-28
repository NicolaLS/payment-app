@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package xyz.lilsus.papp.domain.usecases

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import xyz.lilsus.papp.data.settings.OnboardingRepositoryImpl
import xyz.lilsus.papp.data.settings.WalletSettingsRepositoryImpl
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletType

class ObserveOnboardingRequiredUseCaseTest {

    @Test
    fun requiresOnboardingWhenNoWalletsExistAndOnboardingIsIncomplete() = runTest {
        val onboardingRepository = OnboardingRepositoryImpl(settings = MapSettings())
        val walletRepository = WalletSettingsRepositoryImpl(settings = MapSettings())
        val useCase = ObserveOnboardingRequiredUseCase(
            onboardingRepository = onboardingRepository,
            walletSettingsRepository = walletRepository
        )

        val onboardingRequired = useCase().first()

        assertTrue(onboardingRequired)
    }

    @Test
    fun doesNotRequireOnboardingWhenCompletionWasAlreadyPersisted() = runTest {
        val onboardingRepository = OnboardingRepositoryImpl(settings = MapSettings())
        val walletRepository = WalletSettingsRepositoryImpl(settings = MapSettings())
        onboardingRepository.markOnboardingCompleted()
        val useCase = ObserveOnboardingRequiredUseCase(
            onboardingRepository = onboardingRepository,
            walletSettingsRepository = walletRepository
        )

        val onboardingRequired = useCase().first()

        assertFalse(onboardingRequired)
    }

    @Test
    fun repairsMissingCompletionFlagWhenWalletAlreadyExists() = runTest {
        val onboardingRepository = OnboardingRepositoryImpl(settings = MapSettings())
        val walletRepository = WalletSettingsRepositoryImpl(settings = MapSettings())
        walletRepository.saveWalletConnection(existingWallet(), activate = true)
        val useCase = ObserveOnboardingRequiredUseCase(
            onboardingRepository = onboardingRepository,
            walletSettingsRepository = walletRepository
        )

        val onboardingRequired = useCase().first()

        assertFalse(onboardingRequired)
        assertTrue(onboardingRepository.hasCompletedOnboarding.first())
    }

    private fun existingWallet(): WalletConnection = WalletConnection(
        walletPublicKey = "blink-existing-wallet",
        alias = "Blink",
        type = WalletType.BLINK
    )
}
