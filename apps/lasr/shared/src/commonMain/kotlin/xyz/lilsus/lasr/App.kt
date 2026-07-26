package xyz.lilsus.lasr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import xyz.lilsus.lasr.integration.nwc.createNwcWallet
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.network.createNetworkConnectivity
import xyz.lilsus.raylsuite.core.settings.rememberSecureSettings
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.contacts.rememberContactsRepository
import xyz.lilsus.raylsuite.feature.currencysettings.rememberCurrencyPreferences
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.payment.PaymentCoordinator
import xyz.lilsus.raylsuite.feature.payment.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentsettings.rememberPaymentPreferencesRepository
import xyz.lilsus.raylsuite.feature.themesettings.rememberThemePreferences
import xyz.lilsus.raylsuite.integration.exchangerate.CoinGeckoBitcoinPriceProvider
import xyz.lilsus.raylsuite.integration.lnurl.KtorLnurlPayClient

@Composable
fun App() {
    val themePreferences = rememberThemePreferences(storageName = LASR_PREFERENCES)
    val themePreference by
        themePreferences.preference.collectAsState(
            initial = ThemePreference.System
        )
    val currencyPreferences = rememberCurrencyPreferences(LASR_PREFERENCES)
    val paymentPreferences = rememberPaymentPreferencesRepository(LASR_PREFERENCES)
    val contactsRepository = rememberContactsRepository(LASR_PREFERENCES)
    val secureSettings = rememberSecureSettings(LASR_CREDENTIALS)
    val networkConnectivity = remember { createNetworkConnectivity() }
    val haptics = rememberHapticFeedbackManager()
    val walletScope = rememberCoroutineScope()
    val nwcWallet =
        remember(secureSettings, walletScope) {
            createNwcWallet(
                secureSettings = secureSettings,
                scope = walletScope
            )
        }
    val bitcoinPriceProvider = remember { CoinGeckoBitcoinPriceProvider() }
    val lnurlPayClient =
        remember(networkConnectivity) {
            KtorLnurlPayClient(networkConnectivity)
        }
    val paymentCoordinator =
        remember(
            nwcWallet,
            lnurlPayClient,
            bitcoinPriceProvider,
            currencyPreferences,
            paymentPreferences,
            contactsRepository,
            haptics
        ) {
            PaymentCoordinator(
                paymentProvider = nwcWallet,
                lnurlPayClient = lnurlPayClient,
                bitcoinPriceProvider = bitcoinPriceProvider,
                currencyPreferences = currencyPreferences,
                paymentPreferences = paymentPreferences,
                contactsRepository = contactsRepository,
                haptics = haptics
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
        remember(nwcWallet) {
            if (nwcWallet.connection.value == null) {
                LasrDestination.Welcome
            } else {
                LasrDestination.Home
            }
        }

    DisposableEffect(onboardingViewModel, paymentCoordinator) {
        onDispose {
            onboardingViewModel.clear()
            paymentCoordinator.clear()
        }
    }
    LaunchedEffect(navController, nwcWallet, paymentCoordinator) {
        LasrDeepLinks.events.collect { uri ->
            val scheme = uri.substringBefore(":", missingDelimiterValue = "")
            if (scheme.equals(NWC_SCHEME, ignoreCase = true)) {
                navController.navigate(
                    LasrDestination.ConfirmWallet(
                        uri = normalizeNwcUri(uri),
                        fromSettings = false
                    )
                ) {
                    launchSingleTop = true
                }
                return@collect
            }
            if (!isPaymentScheme(scheme) || nwcWallet.connection.value == null) {
                return@collect
            }

            navController.navigate(LasrDestination.Home) {
                popUpTo<LasrDestination.Home> {
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
            lasrOnboarding(
                navController = navController,
                nwcWallet = nwcWallet,
                onboardingViewModel = onboardingViewModel
            )
            lasrHome(
                navController = navController,
                themePreferences = themePreferences,
                bitcoinPriceProvider = bitcoinPriceProvider,
                currencyPreferences = currencyPreferences,
                paymentPreferences = paymentPreferences,
                contactsRepository = contactsRepository,
                paymentCoordinator = paymentCoordinator,
                nwcWallet = nwcWallet
            )
        }
    }
}

internal const val LASR_PREFERENCES = "lasr_preferences"
private const val LASR_CREDENTIALS = "lasr_wallet"
private const val NWC_SCHEME = "nostr+walletconnect"
private const val LIGHTNING_SCHEME = "lightning"
private const val BITCOIN_SCHEME = "bitcoin"
private const val LNURL_SCHEME = "lnurl"

private fun normalizeNwcUri(uri: String): String =
    if (uri.startsWith("$NWC_SCHEME://", ignoreCase = true)) {
        uri
    } else {
        val value = uri.substringAfter(":", missingDelimiterValue = "").trimStart('/')
        "$NWC_SCHEME://$value"
    }

private fun isPaymentScheme(scheme: String): Boolean =
    scheme.equals(LIGHTNING_SCHEME, ignoreCase = true) ||
        scheme.equals(BITCOIN_SCHEME, ignoreCase = true) ||
        scheme.equals(LNURL_SCHEME, ignoreCase = true)
