package xyz.lilsus.blip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import xyz.lilsus.blip.feature.onboarding.BlipOnboardingDestination
import xyz.lilsus.blip.feature.onboarding.blipOnboarding
import xyz.lilsus.blip.feature.payment.PaymentDeepLinkEvents
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings
import xyz.lilsus.raylsuite.core.settings.rememberSecureSettings
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.platform.rememberRetainedInstance
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics

/** Blip's Android Compose host. iOS has a native SwiftUI composition root. */
@Composable
fun App(performanceDiagnostics: PerformanceDiagnostics? = null) {
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
    val themePreference by
        runtime.themePreferences.preference.collectAsStateWithLifecycle(
            initialValue = ThemePreference.System
        )
    val navController = rememberNavController()
    val startDestination =
        remember(runtime) {
            if (runtime.blinkWallet.connection.value == null) {
                BlipOnboardingDestination.Welcome
            } else {
                BlipDestination.Home
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
                blinkWallet = runtime.blinkWallet,
                onboardingViewModel = runtime.onboardingViewModel,
                contactsRepository = runtime.contactsRepository,
                onFinished = {
                    navController.navigate(BlipDestination.Home) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
            blipHome(
                runtime = runtime,
                performanceDiagnostics = performanceDiagnostics,
                onRemoveWallet = {
                    runtime.resetPaymentSession()
                    PaymentDeepLinkEvents.clear()
                    // Hub targets and app preferences intentionally survive disconnect.
                    runtime.blinkWallet.disconnect()
                    navController.navigate(BlipOnboardingDestination.Welcome) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private const val BLIP_RUNTIME_KEY = "blip-runtime"
