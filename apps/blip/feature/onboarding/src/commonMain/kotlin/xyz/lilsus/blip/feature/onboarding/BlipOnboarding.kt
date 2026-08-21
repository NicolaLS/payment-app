package xyz.lilsus.blip.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.blinkcontacts.BlinkContactsImportEvent
import xyz.lilsus.blip.feature.blinkcontacts.BlinkContactsImportScreen
import xyz.lilsus.blip.feature.blinkcontacts.BlinkContactsImportViewModel
import xyz.lilsus.blip.feature.onboarding.generated.resources.Res
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_agreement_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_autopay_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page1_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page1_subtitle
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page1_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page2_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page2_subtitle
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page2_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page3_body
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page3_subtitle
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_features_page3_title
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_welcome_subtitle_line1
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_welcome_subtitle_line2
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_welcome_title
import xyz.lilsus.blip.feature.walletconnection.AddBlinkWalletEvent
import xyz.lilsus.blip.feature.walletconnection.AddBlinkWalletScreen
import xyz.lilsus.blip.feature.walletconnection.AddBlinkWalletViewModel
import xyz.lilsus.blip.integration.blink.BlinkWallet
import xyz.lilsus.raylsuite.core.camera.rememberCameraPermissionState
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.contacts.ContactsRepository
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

    @Serializable
    data object BlinkContactsImport : BlipOnboardingDestination
}

fun NavGraphBuilder.blipOnboarding(
    navController: NavController,
    blinkWallet: BlinkWallet,
    onboardingViewModel: OnboardingViewModel,
    contactsRepository: ContactsRepository,
    onFinished: () -> Unit
) {
    composable<BlipOnboardingDestination.Welcome> {
        WelcomeScreen(
            title = stringResource(Res.string.onboarding_welcome_title),
            subtitle = stringResource(Res.string.onboarding_welcome_subtitle_line1),
            description = stringResource(Res.string.onboarding_welcome_subtitle_line2),
            stepIndex = OnboardingStep.Welcome.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onGetStarted = {
                navController.navigate(BlipOnboardingDestination.Features)
            }
        )
    }
    composable<BlipOnboardingDestination.Features> {
        val state by onboardingViewModel.uiState.collectAsState()
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
                navController.navigate(BlipOnboardingDestination.Agreement)
            },
            onBack = navController::navigateUp
        )
    }
    composable<BlipOnboardingDestination.Agreement> {
        val state by onboardingViewModel.uiState.collectAsState()
        AgreementScreen(
            body = stringResource(Res.string.onboarding_agreement_body),
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
            onConnected = {
                navController.navigate(BlipOnboardingDestination.BlinkContactsImport)
            },
            onBack = navController::navigateUp
        )
    }
    composable<BlipOnboardingDestination.BlinkContactsImport> {
        OnboardingBlinkContactsImportDestination(
            blinkWallet = blinkWallet,
            contactsRepository = contactsRepository,
            onFinished = onFinished
        )
    }
}

@Composable
private fun AddWalletDestination(
    blinkWallet: BlinkWallet,
    onConnected: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel = remember(blinkWallet) { AddBlinkWalletViewModel(blinkWallet) }
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        onDispose(viewModel::clear)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                AddBlinkWalletEvent.Success -> onConnected()
                AddBlinkWalletEvent.Cancelled -> onBack()
            }
        }
    }

    AddBlinkWalletScreen(
        state = state,
        onBack = viewModel::cancel,
        onApiKeyChange = viewModel::updateApiKey,
        onSubmit = viewModel::submit
    )
}

@Composable
private fun OnboardingBlinkContactsImportDestination(
    blinkWallet: BlinkWallet,
    contactsRepository: ContactsRepository,
    onFinished: () -> Unit
) {
    val viewModel =
        remember(blinkWallet, contactsRepository) {
            BlinkContactsImportViewModel(
                blinkWallet = blinkWallet,
                contactsRepository = contactsRepository
            )
        }
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        onDispose(viewModel::clear)
    }
    LaunchedEffect(viewModel) {
        viewModel.loadBlinkContacts()
        viewModel.events.collectLatest { event ->
            when (event) {
                is BlinkContactsImportEvent.Imported -> onFinished()
            }
        }
    }

    BlinkContactsImportScreen(
        state = state,
        onBack = onFinished,
        onToggleContact = viewModel::toggleBlinkContact,
        onToggleAll = viewModel::toggleAllBlinkContacts,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onImport = viewModel::importSelectedBlinkContacts,
        onSkip = onFinished
    )
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
    WalletInstructions(4)
}

private const val ONBOARDING_STEP_COUNT = 5
