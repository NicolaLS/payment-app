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

private const val NWC_SCHEME = "nostr+walletconnect"

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
