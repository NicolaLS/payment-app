package xyz.lilsus.lasr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics

/** The Compose host used on Android. iOS drives the same content from a native shell. */
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
    val themePreference by
        runtime.themePreferences.preference.collectAsStateWithLifecycle(
            initialValue = ThemePreference.System
        )
    val navController = rememberNavController()
    val startDestination =
        remember(runtime) {
            if (runtime.onboardingState.completed.value) {
                LasrDestination.Home
            } else {
                LasrOnboardingDestination.Welcome
            }
        }

    LaunchedEffect(runtime, navController) {
        runtime.onboardingWalletFlow.collect { requested ->
            if (!requested) return@collect
            runtime.walletFlowHandled()
            navController.navigate(
                LasrOnboardingDestination.ConfirmWallet(fromSettings = false)
            ) {
                launchSingleTop = true
            }
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
                nwcWallet = runtime.nwcWallet,
                onboardingViewModel = runtime.onboardingViewModel,
                connectionDraft = runtime.connectionDraft,
                onWalletConnected = {
                    runtime.completeOnboarding()
                    navController.navigate(LasrDestination.Home) {
                        popUpTo(navController.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
            lasrHome(
                runtime = runtime,
                performanceDiagnostics = performanceDiagnostics
            )
        }
    }
}

private const val LASR_RUNTIME_KEY = "lasr-runtime"
