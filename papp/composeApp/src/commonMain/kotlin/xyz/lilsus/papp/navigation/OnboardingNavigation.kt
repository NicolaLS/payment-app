package xyz.lilsus.papp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.OnboardingRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.domain.usecases.ObserveOnboardingRequiredUseCase
import xyz.lilsus.papp.presentation.common.rememberRetainedInstance
import xyz.lilsus.papp.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.papp.presentation.onboarding.OnboardingViewModel
import xyz.lilsus.papp.presentation.onboarding.screens.AddWalletInstructionsScreen
import xyz.lilsus.papp.presentation.onboarding.screens.AgreementScreen
import xyz.lilsus.papp.presentation.onboarding.screens.AutoPaySettingsScreen
import xyz.lilsus.papp.presentation.onboarding.screens.FeaturesScreen
import xyz.lilsus.papp.presentation.onboarding.screens.NoWalletHelpScreen
import xyz.lilsus.papp.presentation.onboarding.screens.WalletTypeChoiceScreen
import xyz.lilsus.papp.presentation.onboarding.screens.WelcomeScreen

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
            // Completion handler runs at the welcome screen level
            OnboardingCompletionHandler(navController = navController)

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
            val fiatEquivalent = uiState.thresholdFiatEquivalent?.let { formatter.format(it) }

            AutoPaySettingsScreen(
                confirmationMode = uiState.confirmationMode,
                thresholdSats = uiState.thresholdSats,
                fiatEquivalent = fiatEquivalent,
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
            val koin = remember { KoinPlatformTools.defaultContext().get() }
            val walletSettingsRepository = remember { koin.get<WalletSettingsRepository>() }
            val onboardingRepository = remember { koin.get<OnboardingRepository>() }
            val coroutineScope = rememberCoroutineScope()
            val hasExistingWallets by walletSettingsRepository.wallets
                .collectAsState(initial = emptyList())

            AddWalletInstructionsScreen(
                walletType = walletType,
                showSkipButton = hasExistingWallets.isNotEmpty(),
                onConnectWallet = {
                    when (walletType) {
                        WalletType.NWC -> onNavigateToAddNwcWallet()
                        WalletType.BLINK -> onNavigateToAddBlinkWallet()
                    }
                },
                onSkip = {
                    coroutineScope.launch {
                        onboardingRepository.markOnboardingCompleted()
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun OnboardingCompletionHandler(navController: NavController) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }

    // Watch for wallet being added - when a NEW wallet is added during onboarding,
    // mark onboarding as completed so it won't show again.
    val walletSettingsRepository = remember { koin.get<WalletSettingsRepository>() }
    val onboardingRepository = remember { koin.get<OnboardingRepository>() }
    LaunchedEffect(Unit) {
        // Get initial wallet count
        val initialCount = walletSettingsRepository.wallets.first().size
        // Wait for wallet count to increase
        walletSettingsRepository.wallets
            .filter { it.size > initialCount }
            .first()
        // Mark onboarding as completed
        onboardingRepository.markOnboardingCompleted()
    }

    // Observe onboarding required - when completed, navigate to Pay
    val observeOnboardingRequired = remember { koin.get<ObserveOnboardingRequiredUseCase>() }
    LaunchedEffect(Unit) {
        // Wait for onboarding to no longer be required
        observeOnboardingRequired().filterNot { it }.first()
        navController.navigateFromOnboardingToPay()
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
