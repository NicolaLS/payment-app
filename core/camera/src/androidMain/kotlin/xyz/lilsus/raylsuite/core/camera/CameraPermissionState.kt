package xyz.lilsus.raylsuite.core.camera

import androidx.compose.runtime.Stable

@Stable
interface CameraPermissionState {
    val authorization: CameraAuthorizationState

    val hasPermission: Boolean
        get() = authorization.isAuthorized

    /** Whether Android can still present its permission dialog instead of requiring Settings. */
    val canRequestPermission: Boolean

    fun request()

    fun openSettings()

    fun refresh()
}
