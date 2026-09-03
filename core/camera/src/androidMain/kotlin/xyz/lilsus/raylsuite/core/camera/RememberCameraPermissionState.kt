package xyz.lilsus.raylsuite.core.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context.findActivity()
    val authorization =
        remember {
            mutableStateOf(cameraAuthorizationState(context))
        }
    val canRequestPermission =
        remember {
            mutableStateOf(canRequestCameraPermission(context, activity))
        }
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            context.cameraPermissionPreferences()
                .edit()
                .putBoolean(CAMERA_PERMISSION_REQUESTED, true)
                .apply()
            authorization.value =
                if (granted) {
                    CameraAuthorizationState.AUTHORIZED
                } else {
                    CameraAuthorizationState.DENIED
                }
            canRequestPermission.value = canRequestCameraPermission(context, activity)
        }

    val state = remember(context.applicationContext, activity, launcher) {
        AndroidCameraPermissionState(
            context = context.applicationContext,
            activity = activity,
            authorizationState = authorization,
            canRequestPermissionState = canRequestPermission,
            launcher = launcher
        )
    }
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

private class AndroidCameraPermissionState(
    private val context: Context,
    private val activity: Activity?,
    private val authorizationState: MutableState<CameraAuthorizationState>,
    private val canRequestPermissionState: MutableState<Boolean>,
    private val launcher: ActivityResultLauncher<String>
) : CameraPermissionState {
    override val authorization: CameraAuthorizationState
        get() = authorizationState.value

    override val canRequestPermission: Boolean
        get() = canRequestPermissionState.value

    override fun request() {
        refresh()
        if (
            authorizationState.value == CameraAuthorizationState.NOT_DETERMINED ||
            canRequestPermissionState.value
        ) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun openSettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun refresh() {
        authorizationState.value = cameraAuthorizationState(context)
        canRequestPermissionState.value = canRequestCameraPermission(context, activity)
    }
}

private fun cameraAuthorizationState(context: Context): CameraAuthorizationState {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
        return CameraAuthorizationState.UNAVAILABLE
    }
    if (
        context.checkSelfPermission(Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    ) {
        return CameraAuthorizationState.AUTHORIZED
    }
    return if (
        context.cameraPermissionPreferences()
            .getBoolean(CAMERA_PERMISSION_REQUESTED, false)
    ) {
        CameraAuthorizationState.DENIED
    } else {
        CameraAuthorizationState.NOT_DETERMINED
    }
}

private fun Context.cameraPermissionPreferences() =
    getSharedPreferences(CAMERA_PERMISSION_PREFERENCES, Context.MODE_PRIVATE)

private const val CAMERA_PERMISSION_PREFERENCES = "camera_permission_state"
private const val CAMERA_PERMISSION_REQUESTED = "requested"

private fun canRequestCameraPermission(context: Context, activity: Activity?): Boolean {
    if (cameraAuthorizationState(context) == CameraAuthorizationState.NOT_DETERMINED) return true
    return activity?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
    } == true
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
