package xyz.lilsus.papp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.ui.tooling.preview.Preview
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
    AppTheme {
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
