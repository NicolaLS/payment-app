package xyz.lilsus.flint

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.flint.application.wallet.WalletAccessState
import xyz.lilsus.flint.feature.onboarding.FlintOnboardingDestination
import xyz.lilsus.flint.feature.payment.PaymentCoordinator
import xyz.lilsus.flint.feature.payment.PaymentFlow
import xyz.lilsus.flint.feature.payment.flintPaymentErrorMessageFor
import xyz.lilsus.flint.feature.payment.getFlintPaymentErrorMessageFor
import xyz.lilsus.flint.feature.walletconnection.WalletAction
import xyz.lilsus.flint.feature.walletconnection.WalletViewModel
import xyz.lilsus.flint.generated.resources.Res
import xyz.lilsus.flint.generated.resources.app_name
import xyz.lilsus.flint.generated.resources.settings_wallet_subtitle
import xyz.lilsus.flint.generated.resources.settings_wallet_title
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.core.payment.BitcoinPriceProvider
import xyz.lilsus.raylsuite.feature.currencysettings.CurrencyPreferences
import xyz.lilsus.raylsuite.feature.languagesettings.LanguageRepository
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubLensPreferences
import xyz.lilsus.raylsuite.feature.paymenthub.PaymentHubRepository
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.host.rememberSelectedPaymentHubLens
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition
import xyz.lilsus.raylsuite.feature.paymentsettings.PaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics
import xyz.lilsus.raylsuite.feature.settings.SettingsEntry
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.settings.SettingsLegalLinks
import xyz.lilsus.raylsuite.feature.settings.SettingsStartDestination
import xyz.lilsus.raylsuite.feature.themesettings.ThemePreferences
import xyz.lilsus.raylsuite.feature.walletmanagement.ManagedWallet
import xyz.lilsus.raylsuite.feature.walletmanagement.WalletManagementScreen

internal fun NavGraphBuilder.flintHome(
    navController: NavController,
    paymentCoordinator: PaymentCoordinator,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    languageRepository: LanguageRepository,
    paymentPreferences: PaymentPreferencesRepository,
    paymentHubRepository: PaymentHubRepository,
    paymentHub: PaymentHubController,
    lensPreferences: PaymentHubLensPreferences,
    lensDefinitions: List<PaymentHubLensDefinition>,
    walletViewModel: WalletViewModel,
    performanceDiagnostics: PerformanceDiagnostics?,
    networkLabel: String
) {
    composable<FlintDestination.Home> {
        val lens =
            checkNotNull(rememberSelectedPaymentHubLens(lensPreferences, lensDefinitions)) {
                "Flint registers at least one payment hub lens"
            }
        PaymentFlow(
            coordinator = paymentCoordinator,
            paymentHub = paymentHub,
            lens = lens,
            appTitle = stringResource(Res.string.app_name),
            estimatedFeeHint = null,
            errorMessageFor = ::flintPaymentErrorMessageFor,
            eventErrorMessageFor = ::getFlintPaymentErrorMessageFor,
            onNavigateSettings = { navController.navigate(FlintDestination.Settings) },
            onNavigateLibrary = { navController.navigate(FlintDestination.PaymentHub) }
        )
    }
    composable<FlintDestination.Settings> {
        FlintSettings(
            startDestination = SettingsStartDestination.Overview,
            navController = navController,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            languageRepository = languageRepository,
            paymentPreferences = paymentPreferences,
            paymentHubRepository = paymentHubRepository,
            lensPreferences = lensPreferences,
            lensDefinitions = lensDefinitions,
            paymentCoordinator = paymentCoordinator,
            performanceDiagnostics = performanceDiagnostics
        )
    }
    composable<FlintDestination.PaymentHub> {
        FlintSettings(
            startDestination = SettingsStartDestination.PaymentHub,
            navController = navController,
            themePreferences = themePreferences,
            bitcoinPriceProvider = bitcoinPriceProvider,
            currencyPreferences = currencyPreferences,
            languageRepository = languageRepository,
            paymentPreferences = paymentPreferences,
            paymentHubRepository = paymentHubRepository,
            lensPreferences = lensPreferences,
            lensDefinitions = lensDefinitions,
            paymentCoordinator = paymentCoordinator,
            performanceDiagnostics = performanceDiagnostics
        )
    }
    composable<FlintDestination.WalletManagement> {
        val state by walletViewModel.state.collectAsState()
        LaunchedEffect(state.access) {
            if (state.access == WalletAccessState.NoWallet) {
                navController.navigate(FlintOnboardingDestination.Welcome) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
        WalletManagementScreen(
            wallet =
                if (state.access == WalletAccessState.Connected) {
                    ManagedWallet(
                        id = "spark",
                        title = stringResource(Res.string.settings_wallet_title),
                        details =
                            listOf(
                                stringResource(Res.string.settings_wallet_subtitle),
                                networkLabel
                            )
                    )
                } else {
                    null
                },
            onBack = navController::navigateUp,
            onAddWallet = {
                navController.navigate(FlintOnboardingDestination.AddWalletFromSettings)
            },
            onRemoveWallet = { walletViewModel.dispatch(WalletAction.ConfirmRemoval) }
        )
    }
}

@Composable
private fun FlintSettings(
    startDestination: SettingsStartDestination,
    navController: NavController,
    themePreferences: ThemePreferences,
    bitcoinPriceProvider: BitcoinPriceProvider,
    currencyPreferences: CurrencyPreferences,
    languageRepository: LanguageRepository,
    paymentPreferences: PaymentPreferencesRepository,
    paymentHubRepository: PaymentHubRepository,
    lensPreferences: PaymentHubLensPreferences,
    lensDefinitions: List<PaymentHubLensDefinition>,
    paymentCoordinator: PaymentCoordinator,
    performanceDiagnostics: PerformanceDiagnostics?
) {
    SettingsFlow(
        themePreferences = themePreferences,
        languageRepository = languageRepository,
        bitcoinPriceProvider = bitcoinPriceProvider,
        legalLinks = FLINT_LEGAL_LINKS,
        onBack = navController::navigateUp,
        modifier = Modifier,
        startDestination = startDestination,
        currencyPreferences = currencyPreferences,
        paymentPreferences = paymentPreferences,
        paymentHub = paymentHubRepository,
        lensPreferences = lensPreferences,
        lensDefinitions = lensDefinitions,
        performanceDiagnostics = performanceDiagnostics,
        leadingEntries =
            listOf(
                SettingsEntry(
                    id = "wallet",
                    title = stringResource(Res.string.settings_wallet_title),
                    subtitle = stringResource(Res.string.settings_wallet_subtitle),
                    onClick = { navController.navigate(FlintDestination.WalletManagement) }
                )
            ),
        donationAppName = stringResource(Res.string.app_name),
        onDonate = { amountSats ->
            navController.navigate(FlintDestination.Home) {
                popUpTo<FlintDestination.Home> { inclusive = false }
                launchSingleTop = true
            }
            paymentCoordinator.dispatch(
                PaymentIntent.StartDonation(amountSats, FLINT_DONATION_ADDRESS)
            )
        }
    )
}

private val FLINT_DONATION_ADDRESS =
    checkNotNull(LightningAddress.parse("lilsus@blink.sv"))

private val FLINT_LEGAL_LINKS =
    SettingsLegalLinks(
        privacyPolicyUrl =
            "https://github.com/NicolaLS/rayl-suite/blob/main/docs/legal/flint/privacy.md",
        sourceCodeUrl = "https://github.com/NicolaLS/rayl-suite"
    )
