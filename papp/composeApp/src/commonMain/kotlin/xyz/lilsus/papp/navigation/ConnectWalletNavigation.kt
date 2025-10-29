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

fun NavGraphBuilder.connectWalletDialog(navController: NavController) {
    dialog<ConnectWallet> { navBackStackEntry ->
        val info: ConnectWallet = navBackStackEntry.toRoute()
        ConnectWalletDialog(
            initialUri = info.toUriOrNull(),
            onDismiss = { navController.popBackStack() }
        )
    }
}

fun NavController.navigateToConnectWallet(
    pubKeyHex: String = "",
    relay: String? = null,
    secretHex: String? = null,
    lud16: String? = null,
) {
    // TODO: Handle nwc uri deep link:
    // https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-navigation-deep-links.html
    navigate(route = ConnectWallet(pubKeyHex, relay, secretHex, lud16))
}

private fun ConnectWallet.toUriOrNull(): String? {
    if (pubKeyHex.isBlank() || secretHex.isNullOrBlank()) return null
    val params = buildList {
        add("secret=$secretHex")
        relay?.let { add("relay=$it") }
        lud16?.let { add("lud16=$it") }
    }
    val query = params.joinToString(separator = "&")
    return "nostr+walletconnect://$pubKeyHex" + if (query.isNotEmpty()) "?$query" else ""
}
