package xyz.lilsus.papp.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Platform hooks emit deep link URIs here so the shared Compose layer can react uniformly.
 */
object DeepLinkEvents {
    private val eventsChannel = Channel<String>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<String> = eventsChannel.receiveAsFlow()

    fun emit(uri: String?) {
        if (uri.isNullOrBlank()) return
        eventsChannel.trySend(uri)
    }
}
