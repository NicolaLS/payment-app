package xyz.lilsus.papp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.domain.usecases.ObserveOnboardingRequiredUseCase
import xyz.lilsus.papp.domain.usecases.ObserveThemePreferenceUseCase
import xyz.lilsus.papp.navigation.DeepLinkEvents
import xyz.lilsus.papp.navigation.Onboarding
import xyz.lilsus.papp.navigation.Pay
import xyz.lilsus.papp.navigation.connectWalletDialog
import xyz.lilsus.papp.navigation.navigateFromOnboardingToPay
import xyz.lilsus.papp.navigation.navigateToAddBlinkWallet
import xyz.lilsus.papp.navigation.navigateToAddWallet
import xyz.lilsus.papp.navigation.navigateToConnectWallet
import xyz.lilsus.papp.navigation.navigateToSettings
import xyz.lilsus.papp.navigation.onboardingScreen
import xyz.lilsus.papp.navigation.paymentScreen
import xyz.lilsus.papp.navigation.settingsScreen
import xyz.lilsus.papp.presentation.theme.AppTheme

private const val NWC_SCHEME = "nostr+walletconnect"

@Composable
@Preview
fun App() {
    val navController = rememberNavController()

    val koin = remember {
        runCatching { KoinPlatformTools.defaultContext().get() }.getOrNull()
    }

    val themePreferenceFlow = remember {
        koin?.let { k ->
            runCatching { k.get<ObserveThemePreferenceUseCase>()() }.getOrNull()
        } ?: flowOf(ThemePreference.System)
    }
    val themePreference by themePreferenceFlow.collectAsState(initial = ThemePreference.System)

    // Determine if onboarding should be shown
    val onboardingRequiredFlow = remember {
        koin?.let { k ->
            runCatching { k.get<ObserveOnboardingRequiredUseCase>()() }.getOrNull()
        } ?: flowOf(false)
    }
    val onboardingRequired by onboardingRequiredFlow.collectAsState(initial = null)

    LaunchedEffect(navController) {
        DeepLinkEvents.events.collect { uri ->
            val normalized = uri.trim()
            val scheme = normalized.substringBefore(":")
            if (!scheme.equals(NWC_SCHEME, ignoreCase = true)) return@collect

            val normalizedUri = if (normalized.startsWith("$NWC_SCHEME://", ignoreCase = true)) {
                normalized
            } else {
                val afterScheme = normalized
                    .substringAfter(":", missingDelimiterValue = "")
                    .trimStart('/')
                "$NWC_SCHEME://$afterScheme"
            }
            navController.navigateToConnectWallet(uri = normalizedUri)
        }
    }

    // Wait until we know if onboarding is required
    val startDestination = when (onboardingRequired) {
        null -> return

        // Still loading, don't render NavHost yet
        true -> Onboarding

        false -> Pay
    }

    AppTheme(themePreference = themePreference) {
        NavHost(navController, startDestination = startDestination) {
            onboardingScreen(
                navController = navController,
                onNavigateToAddNwcWallet = {
                    navController.navigateToAddWallet()
                },
                onNavigateToAddBlinkWallet = {
                    navController.navigateToAddBlinkWallet()
                }
            )
            paymentScreen(
                onNavigateToSettings = { navController.navigateToSettings() },
                onNavigateToConnectWallet = { pkh ->
                    navController.navigateToConnectWallet(pubKeyHex = pkh)
                }
            )
            settingsScreen(
                navController = navController,
                onBack = { navController.navigateUp() }
            )
            connectWalletDialog(navController)
        }
    }
}
