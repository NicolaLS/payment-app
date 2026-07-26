package xyz.lilsus.blip

import kotlinx.serialization.Serializable

internal sealed interface BlipDestination {
    @Serializable
    data object Welcome : BlipDestination

    @Serializable
    data object Features : BlipDestination

    @Serializable
    data object AutoPay : BlipDestination

    @Serializable
    data object Agreement : BlipDestination

    @Serializable
    data object WalletInstructions : BlipDestination

    @Serializable
    data object AddWallet : BlipDestination

    @Serializable
    data object Home : BlipDestination

    @Serializable
    data object Settings : BlipDestination

    @Serializable
    data object Contacts : BlipDestination

    @Serializable
    data object ShortcutCreate : BlipDestination
}
