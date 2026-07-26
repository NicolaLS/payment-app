package xyz.lilsus.raylsuite.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val permissionGranted =
        remember {
            mutableStateOf(isCameraPermissionGranted(context))
        }
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionGranted.value = granted
        }

    LaunchedEffect(context) {
        permissionGranted.value = isCameraPermissionGranted(context)
    }

    return remember(context.applicationContext, launcher) {
        AndroidCameraPermissionState(
            context = context.applicationContext,
            permissionState = permissionGranted,
            launcher = launcher
        )
    }
}

private class AndroidCameraPermissionState(
    private val context: Context,
    private val permissionState: MutableState<Boolean>,
    private val launcher: ActivityResultLauncher<String>
) : CameraPermissionState {
    override val hasPermission: Boolean
        get() = permissionState.value

    override fun request() {
        permissionState.value = isCameraPermissionGranted(context)
        if (!permissionState.value) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
}

private fun isCameraPermissionGranted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
