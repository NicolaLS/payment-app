package xyz.lilsus.papp.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Shared lifecycle signals for the app process.
 *
 * Emits when the app returns to the foreground (ON_RESUME).
 * Used to rebuild fragile resources like websocket clients.
 */
class AppLifecycleEvents {
    private val _resumed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val resumed: SharedFlow<Unit> = _resumed

    fun notifyResumed() {
        _resumed.tryEmit(Unit)
    }
}
