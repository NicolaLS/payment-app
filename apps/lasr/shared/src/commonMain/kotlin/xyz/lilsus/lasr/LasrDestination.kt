package xyz.lilsus.lasr

import kotlinx.serialization.Serializable

internal sealed interface LasrDestination {
    @Serializable
    data object Home : LasrDestination

    @Serializable
    data object Settings : LasrDestination

    @Serializable
    data object Contacts : LasrDestination

    @Serializable
    data object ShortcutCreate : LasrDestination

    @Serializable
    data object WalletManagement : LasrDestination

    @Serializable
    data object WalletDetails : LasrDestination
}
