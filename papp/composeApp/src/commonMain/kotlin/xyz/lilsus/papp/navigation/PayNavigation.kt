package xyz.lilsus.papp.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import xyz.lilsus.papp.presentation.main.MainScreen

@Serializable
object Pay

fun NavGraphBuilder.paymentScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToConnectWallet: (String) -> Unit = {},
) {
    composable<Pay> {
        MainScreen(
            onNavigateSettings = onNavigateToSettings,
            onNavigateConnectWallet = onNavigateToConnectWallet
        )
    }
}
