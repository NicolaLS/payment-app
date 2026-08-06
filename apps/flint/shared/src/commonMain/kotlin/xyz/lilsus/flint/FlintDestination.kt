package xyz.lilsus.flint

import kotlinx.serialization.Serializable

internal sealed interface FlintDestination {
    @Serializable
    data object Welcome : FlintDestination

    @Serializable
    data object Features : FlintDestination

    @Serializable
    data object AutoPay : FlintDestination

    @Serializable
    data object Agreement : FlintDestination

    @Serializable
    data object WalletInstructions : FlintDestination

    @Serializable
    data object AddWallet : FlintDestination

    @Serializable
    data object AddWalletFromSettings : FlintDestination

    @Serializable
    data object WalletRecovery : FlintDestination

    @Serializable
    data object Home : FlintDestination

    @Serializable
    data object Settings : FlintDestination

    @Serializable
    data object Contacts : FlintDestination

    @Serializable
    data object ShortcutCreate : FlintDestination

    @Serializable
    data object WalletManagement : FlintDestination
}
