@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class
)

package xyz.lilsus.rayl.blip.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.position
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

@Composable
actual fun ScannerViewport(
    active: Boolean,
    onQrCode: (String) -> Unit,
    onPermissionDenied: () -> Unit,
    modifier: Modifier
) {
    val controller = remember { IosQrScanner() }
    var authorized by remember { mutableStateOf(isCameraAuthorized()) }

    LaunchedEffect(active) {
        if (!active) {
            controller.stop()
            return@LaunchedEffect
        }

        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> {
                authorized = true
                controller.start(onQrCode)
            }

            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        authorized = granted
                        if (granted && active) {
                            controller.start(onQrCode)
                        } else if (!granted) {
                            onPermissionDenied()
                        }
                    }
                }
            }

            AVAuthorizationStatusDenied -> {
                authorized = false
                onPermissionDenied()
            }

            else -> {
                authorized = false
                onPermissionDenied()
            }
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.close() }
    }

    UIKitView(
        factory = { controller.previewView },
        modifier = modifier,
        update = {
            if (active && authorized) {
                controller.start(onQrCode)
            } else {
                controller.stop()
            }
        },
        properties = UIKitInteropProperties(
            isInteractive = false,
            isNativeAccessibilityEnabled = false
        )
    )
}

private fun isCameraAuthorized(): Boolean =
    AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
        AVAuthorizationStatusAuthorized

private class IosQrScanner {
    private val session = AVCaptureSession()
    private val sessionQueue: dispatch_queue_t =
        dispatch_queue_create("xyz.lilsus.blip.qr.session", null)
    private val delegate = QrMetadataDelegate(::emit)
    val previewView = CameraPreviewView(session)

    private var configured = false
    private var running = false
    private var emittedValue: String? = null
    private var onQrCode: ((String) -> Unit)? = null

    fun start(callback: (String) -> Unit) {
        onQrCode = callback
        if (running) return
        running = true
        emittedValue = null
        dispatch_async(sessionQueue) {
            if (!configureIfNeeded()) {
                running = false
                return@dispatch_async
            }
            if (running && !session.running) {
                session.startRunning()
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        emittedValue = null
        dispatch_async(sessionQueue) {
            if (session.running) {
                session.stopRunning()
            }
        }
    }

    fun close() {
        running = false
        onQrCode = null
        dispatch_async(sessionQueue) {
            if (session.running) {
                session.stopRunning()
            }
        }
    }

    private fun configureIfNeeded(): Boolean {
        if (configured) return true
        val device = AVCaptureDevice.devicesWithMediaType(AVMediaTypeVideo)
            .filterIsInstance<AVCaptureDevice>()
            .firstOrNull { it.position == AVCaptureDevicePositionBack }
            ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?: return false

        val input = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            AVCaptureDeviceInput.deviceInputWithDevice(device, error.ptr)
        } ?: return false
        val output = AVCaptureMetadataOutput()

        session.beginConfiguration()
        val success = try {
            if (!session.canAddInput(input) || !session.canAddOutput(output)) {
                false
            } else {
                session.addInput(input)
                session.addOutput(output)
                output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
                output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
                true
            }
        } finally {
            session.commitConfiguration()
        }
        configured = success
        return success
    }

    private fun emit(value: String) {
        val normalized = value.trim()
        if (!running || normalized.isEmpty() || emittedValue == normalized) return
        emittedValue = normalized
        onQrCode?.invoke(normalized)
    }
}

private class CameraPreviewView(session: AVCaptureSession) :
    UIView(frame = CGRectZero.readValue()) {
    private val previewLayer = AVCaptureVideoPreviewLayer.layerWithSession(session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        clipsToBounds = true
        userInteractionEnabled = false
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.frame = bounds
    }
}

private class QrMetadataDelegate(private val onValue: (String) -> Unit) :
    NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        val value = didOutputMetadataObjects
            .asSequence()
            .filterIsInstance<AVMetadataMachineReadableCodeObject>()
            .mapNotNull { code -> code.stringValue }
            .firstOrNull()
            ?: return
        onValue(value)
    }
}
