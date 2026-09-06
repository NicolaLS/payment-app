package xyz.lilsus.blip

import kotlinx.serialization.Serializable

internal sealed interface BlipDestination {
    /** The tab shell. Everything below it is a tab-internal destination. */
    @Serializable
    data object Home : BlipDestination
}
