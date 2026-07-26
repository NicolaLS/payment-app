package xyz.lilsus.blip.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.blip.domain.format.rememberAmountFormatter
import xyz.lilsus.blip.domain.model.WalletType
import xyz.lilsus.blip.presentation.common.rememberRetainedInstance
import xyz.lilsus.blip.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.blip.presentation.onboarding.OnboardingViewModel
import xyz.lilsus.blip.presentation.onboarding.screens.AddWalletInstructionsScreen
import xyz.lilsus.blip.presentation.onboarding.screens.AgreementScreen
import xyz.lilsus.blip.presentation.onboarding.screens.AutoPaySettingsScreen
import xyz.lilsus.blip.presentation.onboarding.screens.FeaturesScreen
import xyz.lilsus.blip.presentation.onboarding.screens.NoWalletHelpScreen
import xyz.lilsus.blip.presentation.onboarding.screens.WalletTypeChoiceScreen
import xyz.lilsus.blip.presentation.onboarding.screens.WelcomeScreen

// Route definitions for onboarding flow
@Serializable
object Onboarding

@Serializable
object OnboardingWelcome

@Serializable
object OnboardingFeatures

@Serializable
object OnboardingAutoPay

@Serializable
object OnboardingWalletChoice

@Serializable
object OnboardingNoWalletHelp

@Serializable
data class OnboardingAgreement(val walletType: String)

@Serializable
data class OnboardingAddWallet(val walletType: String)

fun NavGraphBuilder.onboardingScreen(
    navController: NavController,
    onNavigateToAddNwcWallet: () -> Unit,
    onNavigateToAddBlinkWallet: () -> Unit
) {
    navigation<Onboarding>(startDestination = OnboardingWelcome) {
        composable<OnboardingWelcome> {
            WelcomeScreen(
                onGetStarted = {
                    navController.navigate(OnboardingFeatures)
                }
            )
        }

        composable<OnboardingFeatures> {
            val viewModel = rememberRetainedOnboardingViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val cameraPermission = rememberCameraPermissionState()

            FeaturesScreen(
                currentPage = uiState.featuresPage,
                totalPages = 3,
                onPageChanged = { page -> viewModel.setFeaturesPage(page) },
                onContinue = {
                    navController.navigate(OnboardingAutoPay)
                },
                onRequestCameraPermission = { cameraPermission.request() },
                onBack = { navController.popBackStack() }
            )
        }

        composable<OnboardingAutoPay> {
            val viewModel = rememberRetainedOnboardingViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val formatter = rememberAmountFormatter()
            val secondaryEquivalent = uiState.thresholdSecondaryEquivalent?.let {
                formatter.format(it)
            }

            AutoPaySettingsScreen(
                confirmationMode = uiState.confirmationMode,
                thresholdSats = uiState.thresholdSats,
                secondaryEquivalent = secondaryEquivalent,
                onConfirmationModeChanged = { mode -> viewModel.setConfirmationMode(mode) },
                onThresholdChanged = { threshold -> viewModel.setThreshold(threshold) },
                onContinue = {
                    viewModel.persistAutoPaySettings()
                    navController.navigate(OnboardingWalletChoice)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<OnboardingWalletChoice> {
            WalletTypeChoiceScreen(
                selectedType = null,
                onSelectWalletType = { type ->
                    navController.navigate(OnboardingAgreement(type.name))
                },
                onSelectNoWallet = {
                    navController.navigate(OnboardingNoWalletHelp)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<OnboardingNoWalletHelp> {
            NoWalletHelpScreen(
                onHasWalletNow = { navController.popBackStack() },
                onStartAgain = {
                    navController.popBackStack(route = OnboardingWelcome, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<OnboardingAgreement> { backStackEntry ->
            val route = backStackEntry.toRoute<OnboardingAgreement>()
            val walletType = runCatching { WalletType.valueOf(route.walletType) }
                .getOrDefault(WalletType.NWC)
            val viewModel = rememberRetainedOnboardingViewModel()
            val uiState by viewModel.uiState.collectAsState()

            AgreementScreen(
                hasAgreed = uiState.hasAgreed,
                onAgreementChanged = { agreed -> viewModel.setAgreement(agreed) },
                onContinue = {
                    navController.navigate(OnboardingAddWallet(walletType.name))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable<OnboardingAddWallet> { backStackEntry ->
            val route = backStackEntry.toRoute<OnboardingAddWallet>()
            val walletType = runCatching { WalletType.valueOf(route.walletType) }
                .getOrDefault(WalletType.NWC)

            AddWalletInstructionsScreen(
                walletType = walletType,
                onConnectWallet = {
                    when (walletType) {
                        WalletType.NWC -> onNavigateToAddNwcWallet()
                        WalletType.BLINK -> onNavigateToAddBlinkWallet()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun rememberRetainedOnboardingViewModel(): OnboardingViewModel {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    return rememberRetainedInstance(
        factory = { koin.get<OnboardingViewModel>() },
        onDispose = { it.clear() }
    )
}

fun NavController.navigateToOnboarding() {
    navigate(route = Onboarding) {
        popUpTo(Pay) { inclusive = true }
        launchSingleTop = true
    }
}

fun NavController.navigateFromOnboardingToPay() {
    navigate(route = Pay) {
        popUpTo(Onboarding) { inclusive = true }
        launchSingleTop = true
    }
}
