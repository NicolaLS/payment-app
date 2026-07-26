package xyz.lilsus.lasr

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

object LasrDeepLinks {
    private val eventsChannel =
        Channel<String>(
            capacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

    internal val events: Flow<String> = eventsChannel.receiveAsFlow()

    fun emit(uri: String?) {
        val normalized = uri?.trim()?.takeIf(String::isNotEmpty) ?: return
        eventsChannel.trySend(normalized)
    }
}
