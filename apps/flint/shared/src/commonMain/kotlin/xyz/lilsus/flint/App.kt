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
import xyz.lilsus.flint.feature.payment.PaymentCoordinator
import xyz.lilsus.flint.feature.wallet.WalletViewModel
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.contacts.DefaultContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.DefaultCurrencyPreferences
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.paymentsettings.DefaultPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.themesettings.DefaultThemePreferences

@Composable
fun App(host: FlintAppHost) {
    val appSettings = rememberAppSettings(FLINT_PREFERENCES)
    val themePreferences = remember(appSettings) { DefaultThemePreferences(appSettings) }
    val currencyPreferences = remember(appSettings) { DefaultCurrencyPreferences(appSettings) }
    val paymentPreferences =
        remember(appSettings) { DefaultPaymentPreferencesRepository(appSettings) }
    val contactsRepository = remember(appSettings) { DefaultContactsRepository(appSettings) }
    val themePreference by
        themePreferences.preference.collectAsState(initial = ThemePreference.System)
    val haptics = rememberHapticFeedbackManager()
    val walletAccess = host.walletAccess
    val paymentCoordinator =
        remember(
            walletAccess.payments,
            host.paymentLinks,
            currencyPreferences,
            paymentPreferences,
            contactsRepository,
            haptics
        ) {
            PaymentCoordinator(
                engine = walletAccess.payments,
                paymentLinks = host.paymentLinks,
                currencyPreferences = currencyPreferences,
                paymentPreferences = paymentPreferences,
                contactsRepository = contactsRepository,
                haptics = haptics
            )
        }
    val walletViewModel = viewModel { WalletViewModel(walletAccess) }
    val walletState by walletViewModel.state.collectAsState()
    val onboardingViewModel =
        remember(paymentPreferences, currencyPreferences) {
            OnboardingViewModel(
                paymentPreferences = paymentPreferences,
                currencyPreferences = currencyPreferences
            )
        }
    var navigationReady by remember { mutableStateOf(!walletState.access.isInitialising()) }

    DisposableEffect(onboardingViewModel) {
        onDispose(onboardingViewModel::clear)
    }
    DisposableEffect(paymentCoordinator) {
        onDispose(paymentCoordinator::clear)
    }
    LaunchedEffect(walletState.access) {
        if (!walletState.access.isInitialising()) navigationReady = true
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
                        WalletAccessState.NoWallet -> FlintDestination.Welcome
                        else -> FlintDestination.WalletRecovery
                    }
                }
            NavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                flintOnboarding(
                    navController = navController,
                    onboardingViewModel = onboardingViewModel,
                    walletViewModel = walletViewModel
                )
                flintHome(
                    navController = navController,
                    paymentCoordinator = paymentCoordinator,
                    themePreferences = themePreferences,
                    bitcoinPriceProvider = walletAccess.payments.amountAssistant,
                    currencyPreferences = currencyPreferences,
                    paymentPreferences = paymentPreferences,
                    contactsRepository = contactsRepository,
                    walletViewModel = walletViewModel,
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
