package xyz.lilsus.raylsuite.feature.settings

import kotlinx.coroutines.flow.StateFlow

/** User-controlled collection of anonymous app and camera performance timings. */
interface PerformanceDiagnostics {
    val sharingEnabled: StateFlow<Boolean>

    fun setSharingEnabled(enabled: Boolean)
}
