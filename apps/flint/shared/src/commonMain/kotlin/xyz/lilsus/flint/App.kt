package xyz.lilsus.flint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import xyz.lilsus.flint.application.wallet.WalletAccessState
import xyz.lilsus.flint.feature.onboarding.FlintOnboardingDestination
import xyz.lilsus.flint.feature.onboarding.flintOnboarding
import xyz.lilsus.flint.feature.payment.PaymentCoordinator
import xyz.lilsus.flint.feature.walletconnection.WalletViewModel
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.contacts.DefaultContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.DefaultCurrencyPreferences
import xyz.lilsus.raylsuite.feature.languagesettings.createLanguageRepository
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.paymentsettings.DefaultPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics
import xyz.lilsus.raylsuite.feature.themesettings.DefaultThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider

@Composable
fun App(host: FlintAppHost, performanceDiagnostics: PerformanceDiagnostics? = null) {
    val appSettings = rememberAppSettings(FLINT_PREFERENCES)
    val themePreferences = remember(appSettings) { DefaultThemePreferences(appSettings) }
    val currencyPreferences = remember(appSettings) { DefaultCurrencyPreferences(appSettings) }
    val languageRepository = remember { createLanguageRepository() }
    val paymentPreferences =
        remember(appSettings) { DefaultPaymentPreferencesRepository(appSettings) }
    val contactsRepository = remember(appSettings) { DefaultContactsRepository(appSettings) }
    val bitcoinPriceProvider = remember { CoinGeckoBitcoinPriceProvider() }
    val themePreference by
        themePreferences.preference.collectAsState(initial = ThemePreference.System)
    val haptics = rememberHapticFeedbackManager()
    val walletAccess = host.walletAccess
    val paymentCoordinator =
        remember(
            walletAccess.payments,
            host.paymentLinks,
            bitcoinPriceProvider,
            currencyPreferences,
            paymentPreferences,
            contactsRepository,
            haptics
        ) {
            PaymentCoordinator(
                engine = walletAccess.payments,
                paymentLinks = host.paymentLinks,
                bitcoinPriceProvider = bitcoinPriceProvider,
                currencyPreferences = currencyPreferences,
                paymentPreferences = paymentPreferences,
                contactsRepository = contactsRepository,
                haptics = haptics
            )
        }
    val walletViewModel = viewModel { WalletViewModel(walletAccess) }
    val walletState by walletViewModel.state.collectAsState()
    val onboardingViewModel =
        remember(
            paymentPreferences,
            currencyPreferences,
            bitcoinPriceProvider
        ) {
            OnboardingViewModel(
                paymentPreferences = paymentPreferences,
                currencyPreferences = currencyPreferences,
                bitcoinPriceProvider = bitcoinPriceProvider
            )
        }
    var navigationReady by remember { mutableStateOf(!walletState.access.isInitialising()) }

    DisposableEffect(onboardingViewModel) {
        onDispose(onboardingViewModel::clear)
    }
    DisposableEffect(languageRepository) {
        onDispose(languageRepository::close)
    }
    DisposableEffect(paymentCoordinator) {
        onDispose(paymentCoordinator::clear)
    }
    LaunchedEffect(walletState.access) {
        if (!walletState.access.isInitialising()) navigationReady = true
        if (
            walletState.access == WalletAccessState.NoWallet ||
            walletState.access == WalletAccessState.ResetRequired
        ) {
            paymentCoordinator.resetSession()
        }
    }
    RaylSuiteTheme(themePreference = themePreference) {
        if (!navigationReady) {
            InitialWalletLoading()
        } else {
            val navController = rememberNavController()
            val startDestination =
                remember {
                    when (walletState.access) {
                        WalletAccessState.Connected -> FlintDestination.Home
                        WalletAccessState.NoWallet -> FlintOnboardingDestination.Welcome
                        else -> FlintOnboardingDestination.WalletRecovery
                    }
                }
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                flintOnboarding(
                    navController = navController,
                    onboardingViewModel = onboardingViewModel,
                    walletViewModel = walletViewModel,
                    onWalletConnected = {
                        navController.navigate(FlintDestination.Home) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
                flintHome(
                    navController = navController,
                    paymentCoordinator = paymentCoordinator,
                    themePreferences = themePreferences,
                    bitcoinPriceProvider = bitcoinPriceProvider,
                    currencyPreferences = currencyPreferences,
                    languageRepository = languageRepository,
                    paymentPreferences = paymentPreferences,
                    contactsRepository = contactsRepository,
                    walletViewModel = walletViewModel,
                    performanceDiagnostics = performanceDiagnostics,
                    networkLabel = host.bootstrapConfig.environment.networkLabel
                )
            }
        }
    }
}

@Composable
private fun InitialWalletLoading() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text(
                text = "Flint",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun WalletAccessState.isInitialising(): Boolean =
    this == WalletAccessState.Loading || this == WalletAccessState.Connecting

internal const val FLINT_PREFERENCES = "flint_preferences"
