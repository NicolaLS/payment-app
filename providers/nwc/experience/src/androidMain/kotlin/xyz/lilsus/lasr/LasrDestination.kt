package xyz.lilsus.lasr

import kotlinx.serialization.Serializable

internal sealed interface LasrDestination {
    /** The tab shell. Everything below it is a tab-internal destination. */
    @Serializable
    data object Home : LasrDestination
}
