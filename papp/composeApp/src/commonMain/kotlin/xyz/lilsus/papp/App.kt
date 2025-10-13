package xyz.lilsus.papp

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import org.jetbrains.compose.ui.tooling.preview.Preview
import xyz.lilsus.papp.navigation.*
import xyz.lilsus.papp.presentation.theme.AppTheme


@Composable
@Preview
fun App() {
    val navController = rememberNavController()
    AppTheme {
        NavHost(navController, startDestination = Pay) {
            paymentScreen(
                onNavigateToSettings = { navController.navigateToSettings() },
                onNavigateToConnectWallet = { pkh -> navController.navigateToConnectWallet(pkh) },
            )
            settingsScreen(
                onBack = { navController.navigateUp() },
            )
            connectWalletDialog()
        }
    }
}
