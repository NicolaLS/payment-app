package xyz.lilsus.blip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import xyz.lilsus.blip.feature.payment.PaymentDeepLinkEvents
import xyz.lilsus.blip.feature.payment.PaymentIntent
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings
import xyz.lilsus.raylsuite.core.settings.rememberSecureSettings
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.platform.rememberRetainedInstance
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel

@Composable
fun App() {
    val appSettings = rememberAppSettings(BLIP_PREFERENCES)
    val secureSettings = rememberSecureSettings(BLIP_CREDENTIALS)
    val haptics = rememberHapticFeedbackManager()
    val runtime =
        rememberRetainedInstance(
            key = BLIP_RUNTIME_KEY,
            factory = {
                BlipRuntime(
                    appSettings = appSettings,
                    secureSettings = secureSettings,
                    haptics = haptics
                )
            },
            onDispose = BlipRuntime::clear
        )
    val themePreferences = runtime.themePreferences
    val themePreference by
        themePreferences.preference.collectAsState(
            initial = ThemePreference.System
        )
    val currencyPreferences = runtime.currencyPreferences
    val paymentPreferences = runtime.paymentPreferences
    val contactsRepository = runtime.contactsRepository
    val blinkWallet = runtime.blinkWallet
    val paymentCoordinator = runtime.paymentCoordinator
    val onboardingViewModel =
        remember(paymentPreferences, currencyPreferences) {
            OnboardingViewModel(
                paymentPreferences = paymentPreferences,
                currencyPreferences = currencyPreferences,
                bitcoinPriceProvider = runtime.bitcoinPriceProvider
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

    DisposableEffect(onboardingViewModel) {
        onDispose(onboardingViewModel::clear)
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
                onboardingViewModel = onboardingViewModel,
                contactsRepository = contactsRepository
            )
            blipHome(
                navController = navController,
                themePreferences = themePreferences,
                bitcoinPriceProvider = runtime.bitcoinPriceProvider,
                currencyPreferences = currencyPreferences,
                paymentPreferences = paymentPreferences,
                contactsRepository = contactsRepository,
                paymentCoordinator = paymentCoordinator,
                blinkWallet = blinkWallet,
                onRemoveWallet = {
                    runtime.resetPaymentSession()
                    PaymentDeepLinkEvents.clear()
                    blinkWallet.disconnect()
                    navController.navigate(BlipDestination.Welcome) {
                        popUpTo(navController.graph.id) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

internal const val BLIP_PREFERENCES = "blip_preferences"
private const val BLIP_CREDENTIALS = "blip_wallet"
private const val BLIP_RUNTIME_KEY = "blip-runtime"
