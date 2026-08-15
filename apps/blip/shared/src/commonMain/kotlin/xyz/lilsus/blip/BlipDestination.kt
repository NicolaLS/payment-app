package xyz.lilsus.blip

import kotlinx.serialization.Serializable

internal sealed interface BlipDestination {
    @Serializable
    data object Home : BlipDestination

    @Serializable
    data object Settings : BlipDestination

    @Serializable
    data object Contacts : BlipDestination

    @Serializable
    data object ShortcutCreate : BlipDestination

    @Serializable
    data object WalletManagement : BlipDestination

    @Serializable
    data object WalletDetails : BlipDestination

    @Serializable
    data object BlinkContactsImport : BlipDestination
}
