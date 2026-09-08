package xyz.lilsus.blip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlin.random.Random
import xyz.lilsus.blip.feature.onboarding.BlipOnboardingDestination
import xyz.lilsus.blip.feature.onboarding.blipOnboarding
import xyz.lilsus.raylsuite.core.model.ThemePreference
import xyz.lilsus.raylsuite.core.settings.rememberAppSettings
import xyz.lilsus.raylsuite.core.settings.rememberSecureSettings
import xyz.lilsus.raylsuite.core.ui.platform.LocalProductName
import xyz.lilsus.raylsuite.core.ui.platform.rememberHapticFeedbackManager
import xyz.lilsus.raylsuite.core.ui.platform.rememberRetainedInstance
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.settings.PerformanceDiagnostics
import xyz.lilsus.raylsuite.feature.walletmanagement.WalletRemovalFailure

/** Blip's Android Compose host. iOS has a native SwiftUI composition root. */
@Composable
fun BlinkExperience(
    configuration: BlinkExperienceConfiguration,
    performanceDiagnostics: PerformanceDiagnostics? = null,
    onRemoved: () -> Unit = {},
    onChooseWallet: (() -> Unit)? = null,
    onConnectionChanged: (Boolean) -> Unit = {}
) {
    val appSettings = rememberAppSettings(configuration.preferencesName)
    val secureSettings = rememberSecureSettings(configuration.credentialsName)
    val walletSettings = rememberAppSettings(configuration.walletPreferencesName)
    val haptics = rememberHapticFeedbackManager()
    val runtimeKey = rememberSaveable { "blink-" + Random.nextLong() }
    val runtime = rememberRetainedInstance(
        key = runtimeKey,
        factory = {
            BlipRuntime(appSettings, walletSettings, configuration, secureSettings, haptics)
        },
        onDispose = BlipRuntime::clear,
        releaseOnLeave = true
    )
    LaunchedEffect(runtime) {
        runtime.removed.collect { if (it) onRemoved() }
    }
    val themePreference by
        runtime.themePreferences.preference.collectAsStateWithLifecycle(
            initialValue = ThemePreference.System
        )
    val removalPending by runtime.removalPending.collectAsStateWithLifecycle()
    val removed by runtime.removed.collectAsStateWithLifecycle()
    if (removalPending || removed) {
        RaylSuiteTheme(themePreference = themePreference) {
            if (removed) {
                CircularProgressIndicator()
            } else {
                val isRemoving by runtime.isRemoving.collectAsStateWithLifecycle()
                WalletRemovalFailure(isWorking = isRemoving, onRetry = runtime::removeWallet)
            }
        }
        return
    }
    val connected by runtime.blinkWallet.connection.collectAsStateWithLifecycle()
    LaunchedEffect(runtime, connected) { onConnectionChanged(connected != null) }
    val connectionOnly = remember(runtime) { runtime.onboardingCompleted }
    val navController = rememberNavController()
    val startDestination =
        remember(runtime) {
            if (runtime.blinkWallet.connection.value == null) {
                if (connectionOnly) {
                    BlipOnboardingDestination.AddWallet
                } else if (configuration.welcomeCompleted) {
                    BlipOnboardingDestination.Features
                } else {
                    BlipOnboardingDestination.Welcome
                }
            } else {
                BlipDestination.Home
            }
        }

    CompositionLocalProvider(
        LocalProductName provides configuration.appName
    ) {
        RaylSuiteTheme(themePreference = themePreference) {
            Column(
                modifier = if (connected == null && onChooseWallet != null) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    )
                } else {
                    Modifier
                }
            ) {
                if (connected == null && onChooseWallet != null) {
                    TextButton(onClick = {
                        if (runtime.blinkWallet.connection.value == null) {
                            runtime.clear()
                            onChooseWallet()
                        }
                    }) { Text(stringResource(android.R.string.cancel)) }
                }
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                ) {
                    blipOnboarding(
                        navController = navController,
                        blinkWallet = runtime.blinkWallet,
                        onboardingViewModel = runtime.onboardingViewModel,
                        hubRepository = runtime.paymentHubRepository,
                        connectionOnly = connectionOnly,
                        onFinished = {
                            runtime.completeOnboarding()
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
                            runtime.removeWallet()
                        }
                    )
                }
            }
        }
    }
}
