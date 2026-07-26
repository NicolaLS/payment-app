package xyz.lilsus.lasr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.integration.nwc.createNwcWallet
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.settings.rememberSecureSettings
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.currencysettings.rememberCurrencyPreferences
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.paymentsettings.rememberPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.settings.SettingsFlow
import xyz.lilsus.raylsuite.feature.themesettings.rememberThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider
import xyz.lilsus.raylsuite.lasr.generated.resources.Res
import xyz.lilsus.raylsuite.lasr.generated.resources.app_name
import xyz.lilsus.raylsuite.lasr.generated.resources.open_settings

@Composable
fun App() {
    val themePreferences = rememberThemePreferences(storageName = LASR_PREFERENCES)
    val themePreference by
        themePreferences.preference.collectAsState(
            initial = ThemePreference.System
        )
    val currencyPreferences = rememberCurrencyPreferences(LASR_PREFERENCES)
    val paymentPreferences = rememberPaymentPreferencesRepository(LASR_PREFERENCES)
    val secureSettings = rememberSecureSettings(LASR_CREDENTIALS)
    val walletScope = rememberCoroutineScope()
    val nwcWallet =
        remember(secureSettings, walletScope) {
            createNwcWallet(
                secureSettings = secureSettings,
                scope = walletScope
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
        remember(nwcWallet) {
            if (nwcWallet.connection.value == null) {
                LasrDestination.Welcome
            } else {
                LasrDestination.Home
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
            lasrOnboarding(
                navController = navController,
                nwcWallet = nwcWallet,
                onboardingViewModel = onboardingViewModel
            )
            composable<LasrDestination.Home> {
                AppHome(
                    onOpenSettings = {
                        navController.navigate(LasrDestination.Settings)
                    }
                )
            }
            composable<LasrDestination.Settings> {
                SettingsFlow(
                    storageName = LASR_PREFERENCES,
                    themePreferences = themePreferences,
                    bitcoinPriceProvider = bitcoinPriceProvider,
                    onBack = navController::navigateUp
                )
            }
        }
    }
}

@Composable
private fun AppHome(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(Res.string.app_name))
        Button(onClick = onOpenSettings) {
            Text(stringResource(Res.string.open_settings))
        }
    }
}

internal const val LASR_PREFERENCES = "lasr_preferences"
private const val LASR_CREDENTIALS = "lasr_wallet"
