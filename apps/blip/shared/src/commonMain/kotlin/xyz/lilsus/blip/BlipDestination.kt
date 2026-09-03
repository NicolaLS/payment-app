package xyz.lilsus.blip

import kotlinx.serialization.Serializable

internal sealed interface BlipDestination {
    @Serializable
    data object Home : BlipDestination

    @Serializable
    data object Settings : BlipDestination

    @Serializable
    data object PaymentHub : BlipDestination

    @Serializable
    data object BlinkContactsImport : BlipDestination
}
