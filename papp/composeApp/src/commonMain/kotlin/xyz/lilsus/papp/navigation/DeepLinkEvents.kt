package xyz.lilsus.papp.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Platform hooks emit deep link URIs here so the shared Compose layer can react uniformly.
 */
object DeepLinkEvents {
    private val _events = MutableSharedFlow<String>(
        replay = 1,
        extraBufferCapacity = 1,
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun emit(uri: String?) {
        if (uri.isNullOrBlank()) return
        _events.tryEmit(uri)
    }
}
