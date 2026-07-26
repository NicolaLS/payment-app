package xyz.lilsus.blip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import xyz.lilsus.blip.integration.blink.createBlinkWallet
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.network.createNetworkConnectivity
import xyz.lilsus.raylsuite.core.settings.rememberSecureSettings
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.contacts.rememberContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.rememberCurrencyPreferences
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.payment.PaymentCoordinator
import xyz.lilsus.raylsuite.feature.payment.PaymentDeepLinkEvents
import xyz.lilsus.raylsuite.feature.payment.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentsettings.rememberPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.themesettings.rememberThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider
import xyz.lilsus.raylsuite.integration.lnurl.KtorLnurlPayClient

@Composable
fun App() {
    val themePreferences = rememberThemePreferences(storageName = BLIP_PREFERENCES)
    val themePreference by
        themePreferences.preference.collectAsState(
            initial = ThemePreference.System
        )
    val currencyPreferences = rememberCurrencyPreferences(BLIP_PREFERENCES)
    val paymentPreferences = rememberPaymentPreferencesRepository(BLIP_PREFERENCES)
    val contactsRepository = rememberContactsRepository(BLIP_PREFERENCES)
    val secureSettings = rememberSecureSettings(BLIP_CREDENTIALS)
    val networkConnectivity = remember { createNetworkConnectivity() }
    val haptics = rememberHapticFeedbackManager()
    val blinkWallet =
        remember(secureSettings, networkConnectivity) {
            createBlinkWallet(
                secureSettings = secureSettings,
                isNetworkAvailable = networkConnectivity::isNetworkAvailable
            )
        }
    val bitcoinPriceProvider = remember { CoinGeckoBitcoinPriceProvider() }
    val lnurlPayClient =
        remember(networkConnectivity) {
            KtorLnurlPayClient(networkConnectivity)
        }
    val paymentCoordinator =
        remember(
            blinkWallet,
            lnurlPayClient,
            bitcoinPriceProvider,
            currencyPreferences,
            paymentPreferences,
            contactsRepository,
            haptics
        ) {
            PaymentCoordinator(
                paymentProvider = blinkWallet,
                lnurlPayClient = lnurlPayClient,
                bitcoinPriceProvider = bitcoinPriceProvider,
                currencyPreferences = currencyPreferences,
                paymentPreferences = paymentPreferences,
                contactsRepository = contactsRepository,
                haptics = haptics,
                showEstimatedFeeHint = true
            )
        }
    val onboardingViewModel =
        remember(paymentPreferences, currencyPreferences, bitcoinPriceProvider) {
            OnboardingViewModel(
                paymentPreferences = paymentPreferences,
                currencyPreferences = currencyPreferences,
                bitcoinPriceProvider = bitcoinPriceProvider
            )
        }
    val navController = rememberNavController()
    val startDestination =
        remember(blinkWallet) {
            if (blinkWallet.connection.value == null) {
                BlipDestination.Welcome
            } else {
                BlipDestination.Home
            }
        }

    DisposableEffect(onboardingViewModel, paymentCoordinator) {
        onDispose {
            onboardingViewModel.clear()
            paymentCoordinator.clear()
        }
    }
    LaunchedEffect(navController, blinkWallet, paymentCoordinator) {
        PaymentDeepLinkEvents.events.collect { uri ->
            if (blinkWallet.connection.value == null) return@collect
            navController.navigate(BlipDestination.Home) {
                popUpTo<BlipDestination.Home> {
                    inclusive = false
                }
                launchSingleTop = true
            }
            paymentCoordinator.dispatch(PaymentIntent.DeepLinkReceived(uri))
        }
    }

    RaylSuiteTheme(themePreference = themePreference) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
        ) {
            blipOnboarding(
                navController = navController,
                blinkWallet = blinkWallet,
                onboardingViewModel = onboardingViewModel
            )
            blipHome(
                navController = navController,
                themePreferences = themePreferences,
                bitcoinPriceProvider = bitcoinPriceProvider,
                currencyPreferences = currencyPreferences,
                paymentPreferences = paymentPreferences,
                contactsRepository = contactsRepository,
                paymentCoordinator = paymentCoordinator,
                blinkWallet = blinkWallet
            )
        }
    }
}

internal const val BLIP_PREFERENCES = "blip_preferences"
private const val BLIP_CREDENTIALS = "blip_wallet"
