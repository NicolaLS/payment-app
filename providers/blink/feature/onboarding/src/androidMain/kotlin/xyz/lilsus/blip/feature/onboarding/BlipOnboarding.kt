package xyz.lilsus.blip.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import xyz.lilsus.blip.feature.onboarding.R
import xyz.lilsus.blip.feature.walletconnection.AddBlinkWalletEvent
import xyz.lilsus.blip.feature.walletconnection.AddBlinkWalletScreen
import xyz.lilsus.blip.feature.walletconnection.AddBlinkWalletViewModel
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.raylsuite.core.camera.rememberCameraPermissionState
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.onboarding.AgreementScreen
import xyz.lilsus.raylsuite.feature.onboarding.AutoPaySettingsScreen
import xyz.lilsus.raylsuite.feature.onboarding.FeaturesScreen
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingFeaturePage
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.onboarding.WelcomeScreen

@Serializable
sealed interface BlipOnboardingDestination {
    @Serializable
    data object Welcome : BlipOnboardingDestination

    @Serializable
    data object Features : BlipOnboardingDestination

    @Serializable
    data object AutoPay : BlipOnboardingDestination

    @Serializable
    data object Agreement : BlipOnboardingDestination

    @Serializable
    data object WalletInstructions : BlipOnboardingDestination

    @Serializable
    data object AddWallet : BlipOnboardingDestination
}

fun NavGraphBuilder.blipOnboarding(
    navController: NavController,
    blinkWallet: BlinkWallet,
    onboardingViewModel: OnboardingViewModel,
    connectionOnly: Boolean,
    privacyPolicyUrl: String?,
    termsUrl: String?,
    onFinished: () -> Unit
) {
    composable<BlipOnboardingDestination.Welcome> {
        WelcomeScreen(
            title = stringResource(
                R.string.onboarding_welcome_title,
                xyz.lilsus.raylsuite.core.ui.platform.LocalProductName.current
            ),
            subtitle = stringResource(R.string.onboarding_welcome_subtitle_line1),
            description = stringResource(R.string.onboarding_welcome_subtitle_line2),
            stepIndex = OnboardingStep.Welcome.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onGetStarted = {
                navController.navigate(BlipOnboardingDestination.Features)
            }
        )
    }
    composable<BlipOnboardingDestination.Features> {
        val state by onboardingViewModel.uiState.collectAsStateWithLifecycle()
        val cameraPermission = rememberCameraPermissionState()
        FeaturesScreen(
            pages = onboardingFeaturePages(),
            currentPage = state.featuresPage,
            stepIndex = OnboardingStep.Features.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onPageChanged = onboardingViewModel::setFeaturesPage,
            onContinue = {
                navController.navigate(BlipOnboardingDestination.AutoPay)
            },
            onRequestCameraPermission = cameraPermission::request,
            onBack = navController::navigateUp
        )
    }
    composable<BlipOnboardingDestination.AutoPay> {
        val state by onboardingViewModel.uiState.collectAsStateWithLifecycle()
        val formatter = rememberAmountFormatter()
        AutoPaySettingsScreen(
            body = stringResource(
                R.string.onboarding_autopay_body,
                xyz.lilsus.raylsuite.core.ui.platform.LocalProductName.current
            ),
            confirmationMode = state.confirmationMode,
            thresholdSats = state.thresholdSats,
            currencyEquivalent = state.thresholdCurrencyEquivalent?.let(formatter::format),
            stepIndex = OnboardingStep.AutoPay.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onConfirmationModeChanged = onboardingViewModel::setConfirmationMode,
            onThresholdChanged = onboardingViewModel::setThreshold,
            onContinue = {
                onboardingViewModel.persistAutoPaySettings()
                navController.navigate(BlipOnboardingDestination.Agreement)
            },
            onBack = navController::navigateUp
        )
    }
    composable<BlipOnboardingDestination.Agreement> {
        val state by onboardingViewModel.uiState.collectAsStateWithLifecycle()
        AgreementScreen(
            body = stringResource(
                R.string.onboarding_agreement_body,
                xyz.lilsus.raylsuite.core.ui.platform.LocalProductName.current
            ),
            hasAgreed = state.hasAgreed,
            stepIndex = OnboardingStep.Agreement.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onAgreementChanged = onboardingViewModel::setAgreement,
            onContinue = {
                navController.navigate(BlipOnboardingDestination.WalletInstructions)
            },
            onBack = navController::navigateUp
        )
    }
    composable<BlipOnboardingDestination.WalletInstructions> {
        BlinkWalletInstructionsScreen(
            stepIndex = OnboardingStep.WalletInstructions.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onConnectWallet = {
                navController.navigate(BlipOnboardingDestination.AddWallet)
            },
            onBack = navController::navigateUp
        )
    }
    composable<BlipOnboardingDestination.AddWallet> {
        AddWalletDestination(
            blinkWallet = blinkWallet,
            privacyPolicyUrl = privacyPolicyUrl,
            termsUrl = termsUrl,
            onConnected = onFinished,
            onBack = if (connectionOnly) null else ({ navController.navigateUp() })
        )
    }
}

@Composable
private fun AddWalletDestination(
    blinkWallet: BlinkWallet,
    privacyPolicyUrl: String?,
    termsUrl: String?,
    onConnected: () -> Unit,
    onBack: (() -> Unit)?
) {
    val viewModel = remember(blinkWallet) { AddBlinkWalletViewModel(blinkWallet) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        onDispose(viewModel::clear)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                AddBlinkWalletEvent.Success -> onConnected()
                AddBlinkWalletEvent.Cancelled -> onBack?.invoke()
            }
        }
    }

    AddBlinkWalletScreen(
        state = state,
        privacyPolicyUrl = privacyPolicyUrl,
        termsUrl = termsUrl,
        onBack = onBack?.let { viewModel::cancel },
        onApiKeyChange = viewModel::updateApiKey,
        onSubmit = viewModel::submit
    )
}

@Composable
private fun onboardingFeaturePages(): List<OnboardingFeaturePage> = listOf(
    OnboardingFeaturePage(
        title = stringResource(R.string.onboarding_features_page1_title),
        subtitle = stringResource(R.string.onboarding_features_page1_subtitle),
        body = stringResource(
            R.string.onboarding_features_page1_body,
            xyz.lilsus.raylsuite.core.ui.platform.LocalProductName.current
        )
    ),
    OnboardingFeaturePage(
        title = stringResource(R.string.onboarding_features_page2_title),
        subtitle = stringResource(R.string.onboarding_features_page2_subtitle),
        body = stringResource(
            R.string.onboarding_features_page2_body,
            xyz.lilsus.raylsuite.core.ui.platform.LocalProductName.current
        )
    ),
    OnboardingFeaturePage(
        title = stringResource(R.string.onboarding_features_page3_title),
        subtitle = stringResource(
            R.string.onboarding_features_page3_subtitle,
            xyz.lilsus.raylsuite.core.ui.platform.LocalProductName.current
        ),
        body = stringResource(
            R.string.onboarding_features_page3_body,
            xyz.lilsus.raylsuite.core.ui.platform.LocalProductName.current
        )
    )
)

private enum class OnboardingStep(val index: Int) {
    Welcome(0),
    Features(1),
    AutoPay(2),
    Agreement(3),
    WalletInstructions(4)
}

private const val ONBOARDING_STEP_COUNT = 5
