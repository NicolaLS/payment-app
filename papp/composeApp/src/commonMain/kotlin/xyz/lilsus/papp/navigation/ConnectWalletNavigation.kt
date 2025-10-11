package xyz.lilsus.papp.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import xyz.lilsus.papp.presentation.add_connection.ConnectWalletDialog

@Serializable
internal data class ConnectWallet(
    val pubKeyHex: String,
    val relay: String? = null,
    val secretHex: String? = null,
    val lud16: String? = null,
)

fun NavGraphBuilder.connectWalletDialog() {
    dialog<ConnectWallet> { navBackStackEntry ->
        val info: ConnectWallet = navBackStackEntry.toRoute()
        ConnectWalletDialog(pubKeyHex = info.pubKeyHex)
    }
}

fun NavController.navigateToConnectWallet(pubKeyHex: String) {
    // TODO: Handle nwc uri deep link:
    // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation-deep-links.html
    navigate(route = ConnectWallet(pubKeyHex))
}
