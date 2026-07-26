package xyz.lilsus.raylsuite.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
interface CameraPermissionState {
    val hasPermission: Boolean

    fun request()
}

@Composable
expect fun rememberCameraPermissionState(): CameraPermissionState
