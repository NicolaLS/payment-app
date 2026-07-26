package xyz.lilsus.lasr

import kotlinx.serialization.Serializable

internal sealed interface LasrDestination {
    @Serializable
    data object Welcome : LasrDestination

    @Serializable
    data object Features : LasrDestination

    @Serializable
    data object AutoPay : LasrDestination

    @Serializable
    data object Agreement : LasrDestination

    @Serializable
    data object WalletInstructions : LasrDestination

    @Serializable
    data object AddWallet : LasrDestination

    @Serializable
    data object AddWalletFromSettings : LasrDestination

    @Serializable
    data class ConfirmWallet(val uri: String, val fromSettings: Boolean) : LasrDestination

    @Serializable
    data object Home : LasrDestination

    @Serializable
    data object Settings : LasrDestination
}
