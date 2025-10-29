@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package xyz.lilsus.papp.presentation.main.scan

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.videoZoomFactor
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSLog
import platform.Foundation.valueForKey
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIView
import platform.UIKit.layoutIfNeeded
import kotlinx.cinterop.memScoped
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import kotlinx.cinterop.value

@Composable
actual fun rememberQrScannerController(): QrScannerController {
    return remember { IosQrScannerController() }
}

actual class CameraPreviewSurface internal constructor(val view: UIView) {
    init {
        view.clipsToBounds = true
    }
}

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    var cameraPermissionGranted by remember { mutableStateOf(isCameraAuthorized()) }

    LaunchedEffect(Unit) {
        cameraPermissionGranted = isCameraAuthorized()
    }

    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observer: NSObjectProtocol = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            cameraPermissionGranted = isCameraAuthorized()
        }
        onDispose {
            center.removeObserver(observer)
        }
    }

    return remember {
        object : CameraPermissionState {
            override val hasPermission: Boolean
                get() = cameraPermissionGranted

            override fun request() {
                if (cameraPermissionGranted) return
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        cameraPermissionGranted = granted
                    }
                }
            }
        }
    }
}

@Composable
actual fun CameraPreviewHost(
    controller: QrScannerController,
    visible: Boolean,
    modifier: Modifier,
) {
    val surface = remember { CameraPreviewSurface(UIView()) }

    DisposableEffect(controller, surface, visible) {
        if (visible) {
            controller.bindPreview(surface)
        } else {
            controller.unbindPreview()
        }
        onDispose {
            controller.unbindPreview()
        }
    }

    if (visible) {
        UIKitView(
            modifier = modifier,
            factory = { surface.view }
        )
    }
}

private fun isCameraAuthorized(): Boolean {
    return AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized
}

private class IosQrScannerController : QrScannerController {

    private val session = AVCaptureSession()
    private val sessionQueue: dispatch_queue_t = dispatch_queue_create(
        "xyz.lilsus.papp.qr.session",
        null
    )
    private var onQrCodeScanned: ((String) -> Unit)? = null
    private var configured = false
    private var paused: Boolean = true
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var previewSurface: CameraPreviewSurface? = null
    private var currentDevice: AVCaptureDevice? = null

    private val delegate = MetadataDelegate(
        isPaused = { paused },
        pause = { paused = true },
        emitResult = { value ->
            onQrCodeScanned?.let { callback ->
                dispatch_async(dispatch_get_main_queue()) {
                    callback(value)
                }
            }
        }
    )

    override fun start(onQrCodeScanned: (String) -> Unit) {
        this.onQrCodeScanned = onQrCodeScanned
        dispatch_async(sessionQueue) {
            configureSessionIfNeeded()
            if (!session.running) {
                session.startRunning()
            }
            paused = false
        }
    }

    override fun pause() {
        dispatch_async(sessionQueue) {
            paused = true
        }
    }

    override fun resume() {
        dispatch_async(sessionQueue) {
            if (session.running) {
                paused = false
            }
        }
    }

    override fun stop() {
        dispatch_async(sessionQueue) {
            if (session.running) {
                session.stopRunning()
            }
            paused = true
        }
        previewSurface = null
        currentDevice = null
        dispatch_async(dispatch_get_main_queue()) {
            previewLayer?.removeFromSuperlayer()
            previewLayer = null
        }
        onQrCodeScanned = null
    }

    override fun bindPreview(surface: CameraPreviewSurface) {
        previewSurface = surface
        dispatch_async(dispatch_get_main_queue()) {
            val layer = previewLayer ?: AVCaptureVideoPreviewLayer.layerWithSession(session).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
                connection?.videoOrientation = AVCaptureVideoOrientationPortrait
                previewLayer = this
            }
            layer.removeFromSuperlayer()
            surface.view.layoutIfNeeded()
            layer.frame = surface.view.bounds
            surface.view.layer.addSublayer(layer)
            previewLayer = layer
        }
    }

    override fun unbindPreview() {
        previewSurface = null
        dispatch_async(dispatch_get_main_queue()) {
            previewLayer?.removeFromSuperlayer()
        }
    }

    override fun setZoom(zoomFraction: Float) {
        val device = currentDevice ?: return
        val clamped = zoomFraction.coerceIn(0f, 1f)
        dispatch_async(sessionQueue) {
            memScoped {
                val minZoom = 1.0
                val maxZoom = (device.activeFormat.valueForKey("videoMaxZoomFactor") as? NSNumber)?.doubleValue ?: 4.0
                val target = (minZoom + (maxZoom - minZoom) * clamped).coerceIn(minZoom, maxZoom)
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                if (device.lockForConfiguration(errorPtr.ptr)) {
                    device.videoZoomFactor = target
                    device.unlockForConfiguration()
                }
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun configureSessionIfNeeded() {
        if (configured) return

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetHigh

        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            session.commitConfiguration()
            NSLog("Failed to acquire capture device for QR scanning")
            return
        }
        currentDevice = device

        val input = createDeviceInput(device)
        if (input != null && session.canAddInput(input)) {
            session.addInput(input)
        } else {
            NSLog("Unable to add camera input to capture session")
        }

        val metadataOutput = AVCaptureMetadataOutput()
        if (session.canAddOutput(metadataOutput)) {
            session.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(delegate, sessionQueue)
            metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        } else {
            NSLog("Unable to add metadata output to capture session")
        }

        session.commitConfiguration()
        configured = true
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun createDeviceInput(device: AVCaptureDevice): AVCaptureDeviceInput? = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error.ptr)
        val failure = error.value
        if (failure != null) {
            NSLog("AVCaptureDeviceInput error: ${failure.localizedDescription}")
            null
        } else {
            input
        }
    }
}

private class MetadataDelegate(
    private val isPaused: () -> Boolean,
    private val pause: () -> Unit,
    private val emitResult: (String) -> Unit,
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        if (isPaused()) return
        val code = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject ?: return
        val value = code.stringValue ?: return
        pause()
        emitResult(value)
    }
}
