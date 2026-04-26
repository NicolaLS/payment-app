package xyz.lilsus.papp.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Payment deep links are emitted here after the app-level router has identified
 * them as payment requests rather than wallet connection requests.
 */
object PaymentDeepLinkEvents {
    private val eventsChannel = Channel<String>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<String> = eventsChannel.receiveAsFlow()

    fun emit(uri: String) {
        if (uri.isBlank()) return
        eventsChannel.trySend(uri)
    }
}
