package xyz.lilsus.papp.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import xyz.lilsus.papp.presentation.addconnection.ConnectWalletDialog

@Serializable
internal data class ConnectWallet(
    val uri: String? = null,
    val pubKeyHex: String? = null,
    val relay: String? = null,
    val secretHex: String? = null,
    val lud16: String? = null
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
    uri: String? = null,
    pubKeyHex: String? = null,
    relay: String? = null,
    secretHex: String? = null,
    lud16: String? = null
) {
    navigate(
        route = ConnectWallet(
            uri = uri,
            pubKeyHex = pubKeyHex,
            relay = relay,
            secretHex = secretHex,
            lud16 = lud16
        )
    ) {
        launchSingleTop = true
    }
}

private fun ConnectWallet.toUriOrNull(): String? {
    uri?.let { return it }
    val pubKey = pubKeyHex
    if (pubKey.isNullOrBlank() || secretHex.isNullOrBlank()) return null
    val params = buildList {
        add("secret=$secretHex")
        relay?.let { add("relay=$it") }
        lud16?.let { add("lud16=$it") }
    }
    val query = params.joinToString(separator = "&")
    return "nostr+walletconnect://$pubKey" + if (query.isNotEmpty()) "?$query" else ""
}
