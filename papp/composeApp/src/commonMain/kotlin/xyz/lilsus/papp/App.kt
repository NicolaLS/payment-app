package xyz.lilsus.papp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.ui.tooling.preview.Preview
import xyz.lilsus.papp.navigation.*
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
        }
    }
    AppTheme {
        NavHost(navController, startDestination = Pay) {
            paymentScreen(
                onNavigateToSettings = { navController.navigateToSettings() },
                onNavigateToConnectWallet = { pkh -> navController.navigateToConnectWallet(pubKeyHex = pkh) },
            )
            settingsScreen(
                navController = navController,
                onBack = { navController.navigateUp() },
            )
            connectWalletDialog(navController)
        }
    }
}
