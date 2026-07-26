package xyz.lilsus.blip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import xyz.lilsus.blip.integration.blink.createBlinkWallet
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.network.createNetworkConnectivity
import xyz.lilsus.raylsuite.core.settings.rememberSecureSettings
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.currencysettings.rememberCurrencyPreferences
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.paymentsettings.rememberPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.themesettings.rememberThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider

@Composable
fun App() {
    val themePreferences = rememberThemePreferences(storageName = BLIP_PREFERENCES)
    val themePreference by
        themePreferences.preference.collectAsState(
            initial = ThemePreference.System
        )
    val currencyPreferences = rememberCurrencyPreferences(BLIP_PREFERENCES)
    val paymentPreferences = rememberPaymentPreferencesRepository(BLIP_PREFERENCES)
    val secureSettings = rememberSecureSettings(BLIP_CREDENTIALS)
    val networkConnectivity = remember { createNetworkConnectivity() }
    val blinkWallet =
        remember(secureSettings, networkConnectivity) {
            createBlinkWallet(
                secureSettings = secureSettings,
                isNetworkAvailable = networkConnectivity::isNetworkAvailable
            )
        }
    val bitcoinPriceProvider = remember { CoinGeckoBitcoinPriceProvider() }
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

    DisposableEffect(onboardingViewModel) {
        onDispose(onboardingViewModel::clear)
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
                bitcoinPriceProvider = bitcoinPriceProvider
            )
        }
    }
}

internal const val BLIP_PREFERENCES = "blip_preferences"
private const val BLIP_CREDENTIALS = "blip_wallet"
