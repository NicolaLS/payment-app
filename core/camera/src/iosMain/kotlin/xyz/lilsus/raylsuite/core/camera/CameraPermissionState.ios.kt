@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package xyz.lilsus.raylsuite.core.camera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

fun nativeCameraAuthorizationState(): CameraAuthorizationState =
    when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
        AVAuthorizationStatusNotDetermined -> CameraAuthorizationState.NOT_DETERMINED
        AVAuthorizationStatusAuthorized -> CameraAuthorizationState.AUTHORIZED
        AVAuthorizationStatusDenied -> CameraAuthorizationState.DENIED
        AVAuthorizationStatusRestricted -> CameraAuthorizationState.RESTRICTED
        else -> CameraAuthorizationState.UNAVAILABLE
    }

fun isNativeCameraAuthorized(): Boolean = nativeCameraAuthorizationState().isAuthorized

fun requestNativeCameraPermission(onResult: (CameraAuthorizationState) -> Unit) {
    val current = nativeCameraAuthorizationState()
    if (current != CameraAuthorizationState.NOT_DETERMINED) {
        onResult(current)
        return
    }
    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) {
        dispatch_async(dispatch_get_main_queue()) {
            onResult(nativeCameraAuthorizationState())
        }
    }
}
