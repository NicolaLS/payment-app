package xyz.lilsus.flint

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.flint.application.wallet.WalletAccessState
import xyz.lilsus.flint.feature.wallet.WalletAction
import xyz.lilsus.flint.feature.wallet.WalletConnectionContent
import xyz.lilsus.flint.feature.wallet.WalletConnectionScreen
import xyz.lilsus.flint.feature.wallet.WalletViewModel
import xyz.lilsus.raylsuite.core.camera.rememberCameraPermissionState
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.onboarding.AgreementScreen
import xyz.lilsus.raylsuite.feature.onboarding.AutoPaySettingsScreen
import xyz.lilsus.raylsuite.feature.onboarding.FeaturesScreen
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingFeaturePage
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingScaffold
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.onboarding.WalletInstructionsScreen
import xyz.lilsus.raylsuite.feature.onboarding.WelcomeScreen
import xyz.lilsus.raylsuite.flint.generated.resources.Res
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_add_wallet_intro
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_add_wallet_step1
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_add_wallet_step2
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_add_wallet_step3
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_add_wallet_title
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_agreement_body
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_autopay_body
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page1_body
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page1_subtitle
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page1_title
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page2_body
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page2_subtitle
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page2_title
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page3_body
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page3_subtitle
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_features_page3_title
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_welcome_subtitle_line1
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_welcome_subtitle_line2
import xyz.lilsus.raylsuite.flint.generated.resources.onboarding_welcome_title

internal fun NavGraphBuilder.flintOnboarding(
    navController: NavController,
    onboardingViewModel: OnboardingViewModel,
    walletViewModel: WalletViewModel
) {
    composable<FlintDestination.Welcome> {
        WelcomeScreen(
            title = stringResource(Res.string.onboarding_welcome_title),
            subtitle = stringResource(Res.string.onboarding_welcome_subtitle_line1),
            description = stringResource(Res.string.onboarding_welcome_subtitle_line2),
            stepIndex = OnboardingStep.Welcome.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onGetStarted = { navController.navigate(FlintDestination.Features) }
        )
    }
    composable<FlintDestination.Features> {
        val state by onboardingViewModel.uiState.collectAsState()
        val cameraPermission = rememberCameraPermissionState()
        FeaturesScreen(
            pages = onboardingFeaturePages(),
            currentPage = state.featuresPage,
            stepIndex = OnboardingStep.Features.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onPageChanged = onboardingViewModel::setFeaturesPage,
            onContinue = { navController.navigate(FlintDestination.AutoPay) },
            onRequestCameraPermission = cameraPermission::request,
            onBack = navController::navigateUp
        )
    }
    composable<FlintDestination.AutoPay> {
        val state by onboardingViewModel.uiState.collectAsState()
        val formatter = rememberAmountFormatter()
        AutoPaySettingsScreen(
            body = stringResource(Res.string.onboarding_autopay_body),
            confirmationMode = state.confirmationMode,
            thresholdSats = state.thresholdSats,
            secondaryEquivalent = state.thresholdSecondaryEquivalent?.let(formatter::format),
            stepIndex = OnboardingStep.AutoPay.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onConfirmationModeChanged = onboardingViewModel::setConfirmationMode,
            onThresholdChanged = onboardingViewModel::setThreshold,
            onContinue = {
                onboardingViewModel.persistAutoPaySettings()
                navController.navigate(FlintDestination.Agreement)
            },
            onBack = navController::navigateUp
        )
    }
    composable<FlintDestination.Agreement> {
        val state by onboardingViewModel.uiState.collectAsState()
        AgreementScreen(
            body = stringResource(Res.string.onboarding_agreement_body),
            hasAgreed = state.hasAgreed,
            stepIndex = OnboardingStep.Agreement.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onAgreementChanged = onboardingViewModel::setAgreement,
            onContinue = { navController.navigate(FlintDestination.WalletInstructions) },
            onBack = navController::navigateUp
        )
    }
    composable<FlintDestination.WalletInstructions> {
        WalletInstructionsScreen(
            title = stringResource(Res.string.onboarding_add_wallet_title),
            introduction = stringResource(Res.string.onboarding_add_wallet_intro),
            steps =
                listOf(
                    AnnotatedString(stringResource(Res.string.onboarding_add_wallet_step1)),
                    AnnotatedString(stringResource(Res.string.onboarding_add_wallet_step2)),
                    AnnotatedString(stringResource(Res.string.onboarding_add_wallet_step3))
                ),
            stepIndex = OnboardingStep.Wallet.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onConnectWallet = { navController.navigate(FlintDestination.AddWallet) },
            onBack = navController::navigateUp
        )
    }
    composable<FlintDestination.AddWallet> {
        OnboardingWalletDestination(
            navController = navController,
            walletViewModel = walletViewModel
        )
    }
    composable<FlintDestination.AddWalletFromSettings> {
        val state by walletViewModel.state.collectAsState()
        LaunchedEffect(state.access) {
            if (state.access == WalletAccessState.Connected) {
                navController.navigateUp()
            }
        }
        WalletConnectionScreen(
            state = state,
            onBack = navController::navigateUp,
            dispatch = walletViewModel::dispatch
        )
    }
    composable<FlintDestination.WalletRecovery> {
        val state by walletViewModel.state.collectAsState()
        LaunchedEffect(state.access) {
            if (state.access == WalletAccessState.Connected) {
                navController.navigate(FlintDestination.Home) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            } else if (state.access == WalletAccessState.NoWallet) {
                navController.navigate(FlintDestination.Welcome) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        WalletConnectionScreen(
            state = state,
            onBack = null,
            dispatch = walletViewModel::dispatch
        )
    }
}

@Composable
private fun OnboardingWalletDestination(
    navController: NavController,
    walletViewModel: WalletViewModel
) {
    val state by walletViewModel.state.collectAsState()
    LaunchedEffect(state.access) {
        if (state.access == WalletAccessState.Connected) {
            navController.navigate(FlintDestination.Home) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    OnboardingScaffold(
        stepIndex = OnboardingStep.Wallet.index,
        totalSteps = ONBOARDING_STEP_COUNT,
        onBack = navController::navigateUp
    ) {
        WalletConnectionContent(
            state = state,
            dispatch = walletViewModel::dispatch,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun onboardingFeaturePages(): List<OnboardingFeaturePage> = listOf(
    OnboardingFeaturePage(
        title = stringResource(Res.string.onboarding_features_page1_title),
        subtitle = stringResource(Res.string.onboarding_features_page1_subtitle),
        body = stringResource(Res.string.onboarding_features_page1_body)
    ),
    OnboardingFeaturePage(
        title = stringResource(Res.string.onboarding_features_page2_title),
        subtitle = stringResource(Res.string.onboarding_features_page2_subtitle),
        body = stringResource(Res.string.onboarding_features_page2_body)
    ),
    OnboardingFeaturePage(
        title = stringResource(Res.string.onboarding_features_page3_title),
        subtitle = stringResource(Res.string.onboarding_features_page3_subtitle),
        body = stringResource(Res.string.onboarding_features_page3_body)
    )
)

private enum class OnboardingStep(val index: Int) {
    Welcome(0),
    Features(1),
    AutoPay(2),
    Agreement(3),
    Wallet(4)
}

private const val ONBOARDING_STEP_COUNT = 5
