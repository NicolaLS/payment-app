package xyz.lilsus.lasr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import xyz.lilsus.lasr.feature.onboarding.LasrOnboardingDestination
import xyz.lilsus.lasr.feature.onboarding.lasrOnboarding
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings
import xyz.lilsus.raylsuite.core.settings.rememberSecureSettings
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.platform.rememberRetainedInstance
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.onboarding.OnboardingViewModel
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics

@Composable
fun App(performanceDiagnostics: PerformanceDiagnostics? = null) {
    val appSettings = rememberAppSettings(LASR_PREFERENCES)
    val secureSettings = rememberSecureSettings(LASR_CREDENTIALS)
    val haptics = rememberHapticFeedbackManager()
    val runtime =
        rememberRetainedInstance(
            key = LASR_RUNTIME_KEY,
            factory = {
                LasrRuntime(
                    appSettings = appSettings,
                    secureSettings = secureSettings,
                    haptics = haptics
                )
            },
            onDispose = LasrRuntime::clear
        )
    val themePreferences = runtime.themePreferences
    val themePreference by
        themePreferences.preference.collectAsState(
            initial = ThemePreference.System
        )
    val currencyPreferences = runtime.currencyPreferences
    val languageRepository = runtime.languageRepository
    val paymentPreferences = runtime.paymentPreferences
    val nwcWallet = runtime.nwcWallet
    val paymentCoordinator = runtime.paymentCoordinator
    val onboardingViewModel =
        remember(paymentPreferences, currencyPreferences, runtime.bitcoinPriceProvider) {
            OnboardingViewModel(
                paymentPreferences = paymentPreferences,
                currencyPreferences = currencyPreferences,
                bitcoinPriceProvider = runtime.bitcoinPriceProvider
            )
        }
    val navController = rememberNavController()
    val startDestination =
        remember(nwcWallet) {
            if (nwcWallet.connection.value == null) {
                LasrOnboardingDestination.Welcome
            } else {
                LasrDestination.Home
            }
        }

    DisposableEffect(onboardingViewModel) {
        onDispose(onboardingViewModel::clear)
    }
    LaunchedEffect(navController, nwcWallet, paymentCoordinator) {
        LasrDeepLinks.events.collect { uri ->
            val scheme = uri.substringBefore(":", missingDelimiterValue = "")
            if (scheme.equals(NWC_SCHEME, ignoreCase = true)) {
                runtime.connectionDraft.set(normalizeNwcUri(uri))
                navController.navigate(
                    LasrOnboardingDestination.ConfirmWallet(
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
                onboardingViewModel = onboardingViewModel,
                connectionDraft = runtime.connectionDraft,
                onWalletConnected = {
                    navController.navigate(LasrDestination.Home) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
            lasrHome(
                navController = navController,
                themePreferences = themePreferences,
                bitcoinPriceProvider = runtime.bitcoinPriceProvider,
                currencyPreferences = currencyPreferences,
                languageRepository = languageRepository,
                paymentPreferences = paymentPreferences,
                paymentHubRepository = runtime.paymentHubRepository,
                paymentHub = runtime.paymentHub,
                lensPreferences = runtime.lensPreferences,
                lensDefinitions = runtime.lensDefinitions,
                paymentCoordinator = paymentCoordinator,
                nwcWallet = nwcWallet,
                performanceDiagnostics = performanceDiagnostics,
                onRemoveWallet = {
                    runtime.resetPaymentSession()
                }
            )
        }
    }
}

internal const val LASR_PREFERENCES = "lasr_preferences"
private const val LASR_CREDENTIALS = "lasr_wallet"
private const val LASR_RUNTIME_KEY = "lasr-runtime"
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
