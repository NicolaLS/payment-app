package xyz.lilsus.papp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.domain.model.ThemePreference
import xyz.lilsus.papp.domain.usecases.ObserveThemePreferenceUseCase
import xyz.lilsus.papp.navigation.DeepLinkEvents
import xyz.lilsus.papp.navigation.Pay
import xyz.lilsus.papp.navigation.connectWalletDialog
import xyz.lilsus.papp.navigation.navigateToConnectWallet
import xyz.lilsus.papp.navigation.navigateToSettings
import xyz.lilsus.papp.navigation.paymentScreen
import xyz.lilsus.papp.navigation.settingsScreen
import xyz.lilsus.papp.presentation.theme.AppTheme

@Composable
@Preview
fun App() {
    val navController = rememberNavController()
    val themePreferenceFlow = remember {
        runCatching {
            val koin = KoinPlatformTools.defaultContext().get()
            koin.get<ObserveThemePreferenceUseCase>()()
        }.getOrElse { flowOf(ThemePreference.System) }
    }
    val themePreference by themePreferenceFlow.collectAsState(initial = ThemePreference.System)
    LaunchedEffect(navController) {
        DeepLinkEvents.events.collect { uri ->
            val normalized = uri.trim()
            val isNwc = normalized.startsWith("nostr+walletconnect://", ignoreCase = true)
            if (isNwc) {
                navController.navigateToConnectWallet(uri = normalized)
            }
            // Avoid re-navigating on process recreation by clearing the replay cache
            DeepLinkEvents.consume()
        }
    }
    AppTheme(themePreference = themePreference) {
        NavHost(navController, startDestination = Pay) {
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
