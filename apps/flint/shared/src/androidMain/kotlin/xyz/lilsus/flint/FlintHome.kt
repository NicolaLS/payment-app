package xyz.lilsus.flint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.flint.application.wallet.WalletAccessState
import xyz.lilsus.flint.feature.onboarding.FlintOnboardingDestination
import xyz.lilsus.flint.feature.onboarding.flintOnboarding
import xyz.lilsus.flint.feature.payment.rememberPaymentFlowState
import xyz.lilsus.flint.feature.payment.rememberPaymentMessages
import xyz.lilsus.flint.feature.walletconnection.WalletAction
import xyz.lilsus.flint.feature.walletconnection.WalletViewModel
import xyz.lilsus.flint.generated.resources.Res
import xyz.lilsus.flint.generated.resources.app_name
import xyz.lilsus.flint.generated.resources.settings_wallet_subtitle
import xyz.lilsus.flint.generated.resources.settings_wallet_title
import xyz.lilsus.raylsuite.core.model.CurrencyCatalog
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.appshell.AppTab
import xyz.lilsus.raylsuite.feature.appshell.AppTabScaffold
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubTab
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentui.PaymentRecentScreen
import xyz.lilsus.raylsuite.feature.paymentui.PaymentScanScreen
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics
import xyz.lilsus.raylsuite.feature.settings.SettingsEntry
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.walletmanagement.ManagedWallet
import xyz.lilsus.raylsuite.feature.walletmanagement.WalletManagementScreen

internal fun NavGraphBuilder.flintHome(
    runtime: FlintRuntime,
    performanceDiagnostics: PerformanceDiagnostics?,
    onWalletRemoved: () -> Unit
) {
    composable<FlintDestination.Home> {
        FlintTabs(
            runtime = runtime,
            performanceDiagnostics = performanceDiagnostics,
            onWalletRemoved = onWalletRemoved
        )
    }
}

/** The Android tab shell. iOS renders [FlintTabContent] inside a native `TabView` instead. */
@Composable
private fun FlintTabs(
    runtime: FlintRuntime,
    performanceDiagnostics: PerformanceDiagnostics?,
    onWalletRemoved: () -> Unit
) {
    val tabState = runtime.tabState
    val selectedTab by tabState.selectedTab.collectAsState()
    val flowState = rememberPaymentFlowState(runtime.paymentCoordinator)

    AppTabScaffold(
        selectedTab = selectedTab,
        onTabSelected = tabState::select,
        recentBadgeCount = flowState.newSessionTransactionCount
    ) { tab ->
        FlintTabContent(
            runtime = runtime,
            tab = tab,
            performanceDiagnostics = performanceDiagnostics,
            onWalletRemoved = onWalletRemoved
        )
    }
}

/** One tab's content, with no tab bar of its own. */
@Composable
internal fun FlintTabContent(
    runtime: FlintRuntime,
    tab: AppTab,
    performanceDiagnostics: PerformanceDiagnostics?,
    onWalletRemoved: () -> Unit
) {
    val coordinator = runtime.paymentCoordinator
    val tabState = runtime.tabState
    val selectedTransactionId by tabState.selectedTransactionId.collectAsState()
    val flowState = rememberPaymentFlowState(coordinator)
    val messages = rememberPaymentMessages(coordinator)
    val hubState by runtime.paymentHub.state.collectAsState()
    val currencyCode by runtime.currencyPreferences.code.collectAsState(
        CurrencyCatalog.DEFAULT_CODE
    )

    when (tab) {
        AppTab.Scan ->
            PaymentScanScreen(
                state = flowState,
                messageEvents = messages,
                appTitle = stringResource(Res.string.app_name),
                estimatedFeeHint = null,
                savePrompt = hubState.savePrompt,
                onIntent = coordinator::dispatch,
                onHubIntent = runtime.paymentHub::dispatch,
                onOpenTransaction = tabState::openTransaction
            )

        AppTab.Recent ->
            PaymentRecentScreen(
                state = flowState,
                estimatedFeeHint = null,
                selectedTransactionId = selectedTransactionId,
                onSelectTransaction = tabState::selectTransaction,
                onIntent = coordinator::dispatch
            )

        AppTab.Hub ->
            PaymentHubTab(
                repository = runtime.paymentHubRepository,
                canvasLayout = runtime.canvasLayout,
                controller = runtime.paymentHub,
                preferredCurrencyCode = { currencyCode }
            )

        AppTab.Settings ->
            FlintSettingsTab(
                runtime = runtime,
                performanceDiagnostics = performanceDiagnostics,
                onWalletRemoved = onWalletRemoved,
                onDonate = { amountSats ->
                    tabState.requestScan()
                    coordinator.dispatch(
                        PaymentIntent.StartDonation(amountSats, FLINT_DONATION_ADDRESS)
                    )
                }
            )
    }
}

@Composable
private fun FlintSettingsTab(
    runtime: FlintRuntime,
    performanceDiagnostics: PerformanceDiagnostics?,
    onWalletRemoved: () -> Unit,
    onDonate: (Long) -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(FlintSettingsDestination.Root) }
    val walletViewModel = viewModel { WalletViewModel(runtime.walletAccess) }
    val walletState by walletViewModel.state.collectAsState()

    LaunchedEffect(walletState.access) {
        if (walletState.access == WalletAccessState.NoWallet) onWalletRemoved()
    }

    when (destination) {
        FlintSettingsDestination.Root ->
            SettingsFlow(
                themePreferences = runtime.themePreferences,
                languageRepository = runtime.languageRepository,
                bitcoinPriceProvider = runtime.bitcoinPriceProvider,
                currencyPreferences = runtime.currencyPreferences,
                paymentPreferences = runtime.paymentPreferences,
                legalLinks = FLINT_LEGAL_LINKS,
                modifier = Modifier,
                performanceDiagnostics = performanceDiagnostics,
                leadingEntries =
                    listOf(
                        SettingsEntry(
                            id = "wallet",
                            title = stringResource(Res.string.settings_wallet_title),
                            subtitle = stringResource(Res.string.settings_wallet_subtitle),
                            onClick = { destination = FlintSettingsDestination.WalletManagement }
                        )
                    ),
                donationAppName = stringResource(Res.string.app_name),
                onDonate = onDonate
            )

        FlintSettingsDestination.WalletManagement ->
            WalletManagementScreen(
                wallet =
                    if (walletState.access == WalletAccessState.Connected) {
                        ManagedWallet(
                            id = "spark",
                            title = stringResource(Res.string.settings_wallet_title),
                            details =
                                listOf(
                                    stringResource(Res.string.settings_wallet_subtitle),
                                    runtime.networkLabel
                                )
                        )
                    } else {
                        null
                    },
                onBack = { destination = FlintSettingsDestination.Root },
                onAddWallet = { destination = FlintSettingsDestination.WalletFlow },
                onRemoveWallet = { walletViewModel.dispatch(WalletAction.ConfirmRemoval) }
            )

        FlintSettingsDestination.WalletFlow -> {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = FlintOnboardingDestination.AddWalletFromSettings
            ) {
                flintOnboarding(
                    navController = navController,
                    onboardingViewModel = runtime.onboardingViewModel,
                    walletViewModel = walletViewModel,
                    onWalletConnected = { destination = FlintSettingsDestination.Root }
                )
            }
        }
    }
}

private enum class FlintSettingsDestination {
    Root,
    WalletManagement,
    WalletFlow
}

private val FLINT_DONATION_ADDRESS =
    checkNotNull(LightningAddress.parse("lilsus@blink.sv"))
