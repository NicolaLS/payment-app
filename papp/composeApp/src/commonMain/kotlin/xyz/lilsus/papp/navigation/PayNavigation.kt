package xyz.lilsus.papp.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable
import xyz.lilsus.papp.presentation.main.MainScreen
import xyz.lilsus.papp.presentation.main.MainUiState

@Serializable
object Pay

fun NavGraphBuilder.paymentScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToConnectWallet: (String) -> Unit = {},
) {
    composable<Pay> {
        val uiState = MainUiState.Active // by viewmodel.uiState later..
        MainScreen(
            onNavigateSettings = onNavigateToSettings,
            onNavigateConnectWallet = onNavigateToConnectWallet,
            uiState = uiState
        )
    }
}
