package xyz.lilsus.lasr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.feature.onboarding.LasrOnboardingDestination
import xyz.lilsus.lasr.feature.onboarding.lasrOnboarding
import xyz.lilsus.lasr.feature.payment.rememberPaymentFlowState
import xyz.lilsus.lasr.feature.payment.rememberPaymentMessages
import xyz.lilsus.lasr.feature.walletdetails.NwcWalletDetailsScreen
import xyz.lilsus.lasr.generated.resources.Res
import xyz.lilsus.lasr.generated.resources.app_name
import xyz.lilsus.lasr.integration.nwc.NwcWalletConnection
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
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.Res as WalletRes
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_subtitle

internal fun NavGraphBuilder.lasrHome(
    runtime: LasrRuntime,
    performanceDiagnostics: PerformanceDiagnostics?,
    onRemoveWallet: () -> Unit
) {
    composable<LasrDestination.Home> {
        LasrTabs(
            runtime = runtime,
            performanceDiagnostics = performanceDiagnostics,
            onRemoveWallet = onRemoveWallet
        )
    }
}

/** The Android tab shell. iOS renders [LasrTabContent] inside a native `TabView` instead. */
@Composable
private fun LasrTabs(
    runtime: LasrRuntime,
    performanceDiagnostics: PerformanceDiagnostics?,
    onRemoveWallet: () -> Unit
) {
    val tabState = runtime.tabState
    val selectedTab by tabState.selectedTab.collectAsState()
    val flowState = rememberPaymentFlowState(runtime.paymentCoordinator)

    AppTabScaffold(
        selectedTab = selectedTab,
        onTabSelected = tabState::select,
        recentBadgeCount = flowState.newSessionTransactionCount
    ) { tab ->
        LasrTabContent(
            runtime = runtime,
            tab = tab,
            performanceDiagnostics = performanceDiagnostics,
            onRemoveWallet = onRemoveWallet
        )
    }
}

/** One tab's content, with no tab bar of its own. */
@Composable
internal fun LasrTabContent(
    runtime: LasrRuntime,
    tab: AppTab,
    performanceDiagnostics: PerformanceDiagnostics?,
    onRemoveWallet: () -> Unit
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
            LasrSettingsTab(
                runtime = runtime,
                performanceDiagnostics = performanceDiagnostics,
                onRemoveWallet = onRemoveWallet,
                onDonate = { amountSats ->
                    tabState.requestScan()
                    coordinator.dispatch(
                        PaymentIntent.StartDonation(
                            amountSats = amountSats,
                            address = LASR_DONATION_ADDRESS
                        )
                    )
                }
            )
    }
}

@Composable
private fun LasrSettingsTab(
    runtime: LasrRuntime,
    performanceDiagnostics: PerformanceDiagnostics?,
    onRemoveWallet: () -> Unit,
    onDonate: (Long) -> Unit
) {
    var destination by rememberSaveable { mutableStateOf(LasrSettingsDestination.Root) }
    val connection by runtime.nwcWallet.connection.collectAsState()
    val walletFlowRequested by runtime.settingsWalletFlow.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(walletFlowRequested) {
        if (walletFlowRequested) destination = LasrSettingsDestination.WalletFlow
    }

    when (destination) {
        LasrSettingsDestination.Root ->
            SettingsFlow(
                themePreferences = runtime.themePreferences,
                languageRepository = runtime.languageRepository,
                bitcoinPriceProvider = runtime.bitcoinPriceProvider,
                currencyPreferences = runtime.currencyPreferences,
                paymentPreferences = runtime.paymentPreferences,
                legalLinks = LASR_LEGAL_LINKS,
                modifier = Modifier,
                performanceDiagnostics = performanceDiagnostics,
                leadingEntries =
                    listOf(
                        SettingsEntry(
                            id = "wallet",
                            title = stringResource(WalletRes.string.settings_manage_wallet),
                            subtitle =
                                connection?.let(::formatWalletSubtitle)
                                    ?: stringResource(
                                        WalletRes.string.settings_manage_wallet_subtitle
                                    ),
                            onClick = {
                                destination = LasrSettingsDestination.WalletManagement
                            }
                        )
                    ),
                donationAppName = stringResource(Res.string.app_name),
                onDonate = onDonate
            )

        LasrSettingsDestination.WalletManagement ->
            WalletManagementScreen(
                wallet =
                    connection?.let {
                        ManagedWallet(
                            id = it.walletPublicKey,
                            title =
                                it.alias?.takeIf(String::isNotBlank)
                                    ?: it.walletPublicKey
                        )
                    },
                onBack = { destination = LasrSettingsDestination.Root },
                onAddWallet = { runtime.requestSettingsWalletFlow() },
                onRemoveWallet = {
                    destination = LasrSettingsDestination.Root
                    onRemoveWallet()
                    scope.launch { runtime.nwcWallet.disconnect() }
                },
                onWalletDetails = { destination = LasrSettingsDestination.WalletDetails }
            )

        LasrSettingsDestination.WalletDetails -> {
            val current = connection
            if (current == null) {
                LaunchedEffect(Unit) { destination = LasrSettingsDestination.WalletManagement }
            } else {
                NwcWalletDetailsScreen(
                    connection = current,
                    onBack = { destination = LasrSettingsDestination.WalletManagement }
                )
            }
        }

        LasrSettingsDestination.WalletFlow ->
            LasrWalletFlow(
                runtime = runtime,
                onFinished = {
                    runtime.walletFlowHandled()
                    destination = LasrSettingsDestination.Root
                }
            )
    }
}

/**
 * Connecting a wallet from settings, including an NWC link opened while a wallet is already
 * connected. It runs inside the Settings tab so the tab bar stays put.
 */
@Composable
private fun LasrWalletFlow(runtime: LasrRuntime, onFinished: () -> Unit) {
    val navController = rememberNavController()
    val pendingDraft = runtime.connectionDraft.uri

    LaunchedEffect(navController, pendingDraft) {
        if (pendingDraft != null) {
            navController.navigate(
                LasrOnboardingDestination.ConfirmWallet(fromSettings = false)
            ) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = LasrOnboardingDestination.AddWalletFromSettings
    ) {
        lasrOnboarding(
            navController = navController,
            nwcWallet = runtime.nwcWallet,
            onboardingViewModel = runtime.onboardingViewModel,
            connectionDraft = runtime.connectionDraft,
            onWalletConnected = onFinished
        )
    }
}

private enum class LasrSettingsDestination {
    Root,
    WalletManagement,
    WalletDetails,
    WalletFlow
}

private fun formatWalletSubtitle(connection: NwcWalletConnection): String {
    connection.alias?.takeIf(String::isNotBlank)?.let { return it }
    val publicKey = connection.walletPublicKey
    return if (publicKey.length <= 12) {
        publicKey
    } else {
        "${publicKey.take(6)}…${publicKey.takeLast(4)}"
    }
}

private val LASR_DONATION_ADDRESS =
    LightningAddress(
        username = "lilsus",
        domain = "blink.sv"
    )
