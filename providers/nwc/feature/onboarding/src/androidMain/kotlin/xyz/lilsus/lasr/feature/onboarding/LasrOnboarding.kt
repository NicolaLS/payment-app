package xyz.lilsus.lasr.feature.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import xyz.lilsus.lasr.feature.walletconnection.AddNwcWalletEvent
import xyz.lilsus.lasr.feature.walletconnection.AddNwcWalletScreen
import xyz.lilsus.lasr.feature.walletconnection.AddNwcWalletViewModel
import xyz.lilsus.lasr.feature.walletconnection.ConnectNwcWalletDialog
import xyz.lilsus.lasr.feature.walletconnection.ConnectNwcWalletEvent
import xyz.lilsus.lasr.feature.walletconnection.ConnectNwcWalletViewModel
import xyz.lilsus.lasr.integration.nwc.NwcWallet
import xyz.lilsus.raylsuite.core.camera.CameraAuthorizationState
import xyz.lilsus.raylsuite.core.camera.rememberCameraPermissionState
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.core.ui.platform.readPlainText
import xyz.lilsus.raylsuite.feature.onboarding.AgreementScreen
import xyz.lilsus.raylsuite.feature.onboarding.AutoPaySettingsScreen
import xyz.lilsus.raylsuite.feature.onboarding.FeaturesScreen
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingFeaturePage
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.onboarding.WelcomeScreen

@Serializable
sealed interface LasrOnboardingDestination {
    @Serializable
    data object Welcome : LasrOnboardingDestination

    @Serializable
    data object Features : LasrOnboardingDestination

    @Serializable
    data object AutoPay : LasrOnboardingDestination

    @Serializable
    data object Agreement : LasrOnboardingDestination

    @Serializable
    data object WalletInstructions : LasrOnboardingDestination

    @Serializable
    data object AddWallet : LasrOnboardingDestination

    @Serializable
    data object AddWalletFromSettings : LasrOnboardingDestination

    @Serializable
    data class ConfirmWallet(val fromSettings: Boolean) : LasrOnboardingDestination
}

fun NavGraphBuilder.lasrOnboarding(
    navController: NavController,
    nwcWallet: NwcWallet,
    onboardingViewModel: OnboardingViewModel,
    connectionDraft: NwcConnectionDraft,
    connectionOnly: Boolean = false,
    onWalletConnected: () -> Unit
) {
    composable<LasrOnboardingDestination.Welcome> {
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
                navController.navigate(LasrOnboardingDestination.Features)
            }
        )
    }
    composable<LasrOnboardingDestination.Features> {
        val state by onboardingViewModel.uiState.collectAsStateWithLifecycle()
        val cameraPermission = rememberCameraPermissionState()
        FeaturesScreen(
            pages = onboardingFeaturePages(),
            currentPage = state.featuresPage,
            stepIndex = OnboardingStep.Features.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onPageChanged = onboardingViewModel::setFeaturesPage,
            onContinue = {
                navController.navigate(LasrOnboardingDestination.AutoPay)
            },
            onRequestCameraPermission = cameraPermission::request,
            onBack = navController::navigateUp
        )
    }
    composable<LasrOnboardingDestination.AutoPay> {
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
                navController.navigate(LasrOnboardingDestination.Agreement)
            },
            onBack = navController::navigateUp
        )
    }
    composable<LasrOnboardingDestination.Agreement> {
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
                if (nwcWallet.connection.value == null) {
                    navController.navigate(LasrOnboardingDestination.WalletInstructions)
                } else {
                    onWalletConnected()
                }
            },
            onBack = navController::navigateUp
        )
    }
    composable<LasrOnboardingDestination.WalletInstructions> {
        NwcWalletInstructionsScreen(
            stepIndex = OnboardingStep.WalletInstructions.index,
            totalSteps = ONBOARDING_STEP_COUNT,
            onConnectWallet = {
                if (nwcWallet.connection.value == null) {
                    navController.navigate(LasrOnboardingDestination.AddWallet)
                } else {
                    onWalletConnected()
                }
            },
            onBack = navController::navigateUp
        )
    }
    composable<LasrOnboardingDestination.AddWallet> {
        AddWalletDestination(
            navController = navController,
            fromSettings = false,
            connectionDraft = connectionDraft,
            connectionOnly = connectionOnly
        )
    }
    composable<LasrOnboardingDestination.AddWalletFromSettings> {
        AddWalletDestination(
            navController = navController,
            fromSettings = true,
            connectionDraft = connectionDraft
        )
    }
    dialog<LasrOnboardingDestination.ConfirmWallet> { backStackEntry ->
        val route = backStackEntry.toRoute<LasrOnboardingDestination.ConfirmWallet>()
        ConfirmWalletDestination(
            uri = connectionDraft.uri,
            nwcWallet = nwcWallet,
            onConnected = {
                connectionDraft.clear()
                when (
                    lasrWalletConnectionOutcome(
                        fromSettings = route.fromSettings,
                        hasAgreed = connectionOnly || onboardingViewModel.uiState.value.hasAgreed
                    )
                ) {
                    LasrWalletConnectionOutcome.ResumeOnboarding ->
                        navController.popBackStack()

                    LasrWalletConnectionOutcome.CompleteOnboarding,
                    LasrWalletConnectionOutcome.FinishSettings -> onWalletConnected()
                }
            },
            onCancelled = {
                connectionDraft.clear()
                navController.navigateUp()
            }
        )
    }
}

@Composable
private fun AddWalletDestination(
    navController: NavController,
    fromSettings: Boolean,
    connectionDraft: NwcConnectionDraft,
    connectionOnly: Boolean = false
) {
    val viewModel = remember { AddNwcWalletViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val cameraPermission = rememberCameraPermissionState()

    DisposableEffect(viewModel) {
        onDispose(viewModel::clear)
    }

    fun handleCameraPermissionAction() {
        cameraPermission.refresh()
        when (cameraPermission.authorization) {
            CameraAuthorizationState.NOT_DETERMINED -> cameraPermission.request()

            CameraAuthorizationState.DENIED -> {
                if (cameraPermission.canRequestPermission) {
                    cameraPermission.request()
                } else {
                    cameraPermission.openSettings()
                }
            }

            else -> Unit
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AddNwcWalletEvent.Confirm -> {
                    connectionDraft.set(event.uri)
                    if (!connectionOnly) navController.popBackStack()
                    navController.navigate(
                        LasrOnboardingDestination.ConfirmWallet(
                            fromSettings = fromSettings
                        )
                    )
                }
            }
        }
    }
    LaunchedEffect(cameraPermission.authorization) {
        if (cameraPermission.authorization == CameraAuthorizationState.NOT_DETERMINED) {
            cameraPermission.request()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AddNwcWalletScreen(
            state = state,
            onBack = if (connectionOnly) null else ({ navController.navigateUp() }),
            onUriChange = viewModel::updateUri,
            onPaste = {
                scope.launch {
                    val text = clipboard.getClipEntry()?.readPlainText()
                    viewModel.prefillUriIfValid(text)
                }
            },
            onSubmit = viewModel::submit,
            onQrCodeScanned = viewModel::handleScannedValue,
            onCameraPermissionAction = ::handleCameraPermissionAction,
            cameraAuthorization = cameraPermission.authorization,
            canRequestCameraPermission = cameraPermission.canRequestPermission
        )
    }
}

@Composable
private fun ConfirmWalletDestination(
    uri: String?,
    nwcWallet: NwcWallet,
    onConnected: () -> Unit,
    onCancelled: () -> Unit
) {
    if (uri == null) {
        LaunchedEffect(Unit) {
            onCancelled()
        }
        return
    }
    val viewModel = remember(nwcWallet) { ConnectNwcWalletViewModel(nwcWallet) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(viewModel) {
        onDispose(viewModel::clear)
    }
    LaunchedEffect(viewModel, uri) {
        viewModel.load(uri)
        viewModel.events.collectLatest { event ->
            when (event) {
                is ConnectNwcWalletEvent.Success -> onConnected()
                ConnectNwcWalletEvent.Cancelled -> onCancelled()
            }
        }
    }

    ConnectNwcWalletDialog(
        state = state,
        onAliasChange = viewModel::updateAlias,
        onRetryDiscovery = viewModel::retryDiscovery,
        onConfirm = viewModel::confirm,
        onCancel = viewModel::cancel
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
