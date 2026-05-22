package xyz.lilsus.papp.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Payment deep links are emitted here after the app-level router has identified
 * them as payment requests rather than wallet connection requests.
 */
data class PaymentDeepLinkRequest(
    val input: String,
    val source: PaymentInputSource = PaymentInputSource.DeepLink
)

enum class PaymentInputSource {
    Camera,
    DeepLink
}

object PaymentDeepLinkEvents {
    private val eventsChannel = Channel<PaymentDeepLinkRequest>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: Flow<PaymentDeepLinkRequest> = eventsChannel.receiveAsFlow()

    fun emit(input: String, source: PaymentInputSource = PaymentInputSource.DeepLink) {
        if (input.isBlank()) return
        eventsChannel.trySend(PaymentDeepLinkRequest(input = input, source = source))
    }
}
