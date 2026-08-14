package xyz.lilsus.flint

import kotlinx.serialization.Serializable

internal sealed interface FlintDestination {
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
