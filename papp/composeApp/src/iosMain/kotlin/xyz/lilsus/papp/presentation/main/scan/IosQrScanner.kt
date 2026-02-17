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
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.*

@Composable
actual fun rememberQrScannerController(): QrScannerController = remember {
    IosQrScannerController()
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
    modifier: Modifier
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
            factory = { surface.view },
            modifier = modifier,
            properties = UIKitInteropProperties()
        )
    }
}

private fun isCameraAuthorized(): Boolean =
    AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
        AVAuthorizationStatusAuthorized

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
    private var lifecycleObserver: NSObjectProtocol? = null

    // Debounce repeated payloads. Only accessed from sessionQueue.
    private var lastEmittedValue: String? = null
    private var lastAppliedZoomFactor: Double? = null

    private fun emitIfNew(value: String) {
        // Must be called from sessionQueue
        if (value == lastEmittedValue) return
        lastEmittedValue = value
        onQrCodeScanned?.let { callback ->
            dispatch_async(dispatch_get_main_queue()) {
                callback(value)
            }
        }
    }

    private fun resetLastEmittedValue() {
        // Must be called from sessionQueue
        lastEmittedValue = null
    }

    private val metadataDelegate = MetadataDelegate(
        isPaused = { paused },
        emitIfNew = ::emitIfNew
    )

    override fun start(onQrCodeScanned: (String) -> Unit) {
        this.onQrCodeScanned = onQrCodeScanned

        // Register lifecycle observer to heal scanner when app becomes active
        if (lifecycleObserver == null) {
            lifecycleObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { _ ->
                dispatch_async(sessionQueue) {
                    if (!paused) {
                        ensureSessionRunning()
                    }
                }
            }
        }

        dispatch_async(sessionQueue) {
            ensureSessionRunning()
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
            // Reset debounce so the same QR code can be scanned again after resuming
            resetLastEmittedValue()
            ensureSessionRunning()
            paused = false
        }
    }

    override fun stop() {
        // Remove lifecycle observer to prevent memory leaks
        lifecycleObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        lifecycleObserver = null

        dispatch_async(sessionQueue) {
            if (session.running) {
                session.stopRunning()
            }
            paused = true
            // Reset configured so next start() will reconfigure the session
            configured = false
            lastAppliedZoomFactor = null
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
                val maxZoom =
                    (
                        device.activeFormat.valueForKey(
                            "videoMaxZoomFactor"
                        ) as? NSNumber
                        )?.doubleValue
                        ?: 4.0
                // Use logarithmic scaling for perceptually uniform zoom.
                // This makes equal gesture distances feel like equal zoom changes.
                val target = minZoom * (maxZoom / minZoom).pow(clamped.toDouble())
                val clampedTarget = target.coerceIn(minZoom, maxZoom)
                val previous = lastAppliedZoomFactor
                if (previous != null && abs(previous - clampedTarget) < ZOOM_FACTOR_EPSILON) {
                    return@memScoped
                }
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                if (device.lockForConfiguration(errorPtr.ptr)) {
                    device.videoZoomFactor = clampedTarget
                    lastAppliedZoomFactor = clampedTarget
                    device.unlockForConfiguration()
                }
            }
        }
    }

    private fun ensureSessionRunning() {
        configureSessionIfNeeded()
        if (!session.running) {
            session.startRunning()
        }
        resetFocusAndExposure()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun resetFocusAndExposure() {
        val device = currentDevice ?: return
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            if (device.lockForConfiguration(errorPtr.ptr)) {
                if (device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
                    device.focusMode = AVCaptureFocusModeContinuousAutoFocus
                }
                if (device.isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)) {
                    device.exposureMode = AVCaptureExposureModeContinuousAutoExposure
                }
                device.unlockForConfiguration()
            }
        }
    }

    private fun selectDevice(): AVCaptureDevice? {
        // Try ultra-wide camera first for maximum field of view
        val ultraWide = AVCaptureDevice.defaultDeviceWithDeviceType(
            deviceType = AVCaptureDeviceTypeBuiltInUltraWideCamera,
            mediaType = AVMediaTypeVideo,
            position = AVCaptureDevicePositionBack
        )
        if (ultraWide != null) return ultraWide

        // Fallback to default back camera
        return AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun configureDeviceFormat(device: AVCaptureDevice): Boolean {
        // Target: 4:3 aspect ratio, 1920x1440, 60fps
        val targetWidth = 1920
        val targetHeight = 1440
        val targetFps = 60.0

        @Suppress("UNCHECKED_CAST")
        val formats = device.formats as? List<AVCaptureDeviceFormat> ?: return false

        for (format in formats) {
            val desc = format.formatDescription ?: continue
            val dimensions = CMVideoFormatDescriptionGetDimensions(desc)

            val matchesDimensions = dimensions.useContents {
                width == targetWidth && height == targetHeight
            }
            if (!matchesDimensions) continue

            // Check FPS support
            @Suppress("UNCHECKED_CAST")
            val fpsRanges = format.videoSupportedFrameRateRanges as? List<AVFrameRateRange>
                ?: continue
            val supportsTargetFps = fpsRanges.any { it.maxFrameRate >= targetFps }
            if (!supportsTargetFps) continue

            // Found a matching format - configure it
            memScoped {
                val errorPtr = alloc<ObjCObjectVar<NSError?>>()
                if (device.lockForConfiguration(errorPtr.ptr)) {
                    device.activeFormat = format
                    val fpsTimescale = targetFps.toInt()
                    device.activeVideoMinFrameDuration = CMTimeMake(
                        value = 1,
                        timescale = fpsTimescale
                    )
                    device.activeVideoMaxFrameDuration = CMTimeMake(
                        value = 1,
                        timescale = fpsTimescale
                    )
                    device.unlockForConfiguration()
                    return true
                }
            }
        }
        return false
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun configureSessionIfNeeded() {
        if (configured) return

        session.beginConfiguration()

        val device = selectDevice()
        if (device == null) {
            session.sessionPreset = AVCaptureSessionPresetHigh
            session.commitConfiguration()
            NSLog("Failed to acquire capture device for QR scanning")
            return
        }
        currentDevice = device

        // Try to set custom format, use InputPriority preset if successful
        val customFormatSet = configureDeviceFormat(device)
        if (customFormatSet) {
            session.sessionPreset = AVCaptureSessionPresetInputPriority
        } else {
            session.sessionPreset = AVCaptureSessionPresetHigh
        }

        val input = createDeviceInput(device)
        if (input == null || !session.canAddInput(input)) {
            NSLog("Unable to add camera input to capture session")
            session.commitConfiguration()
            // Don't set configured = true, allow retry on next start
            return
        }
        session.addInput(input)

        // Metadata-based QR detection
        val metadataOutput = AVCaptureMetadataOutput()
        if (!session.canAddOutput(metadataOutput)) {
            NSLog("Unable to add metadata output to capture session")
            session.commitConfiguration()
            return
        }
        session.addOutput(metadataOutput)
        metadataOutput.setMetadataObjectsDelegate(metadataDelegate, sessionQueue)
        metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

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
    private val emitIfNew: (String) -> Unit
) : NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        if (isPaused()) return
        val value = selectPreferredMetadataValue(didOutputMetadataObjects) ?: return

        // Debounce handled by controller's lastEmittedValue
        emitIfNew(value)
    }
}

private fun selectPreferredMetadataValue(metadataObjects: List<*>): String? {
    if (metadataObjects.isEmpty()) return null
    if (metadataObjects.size == 1) {
        val code = metadataObjects.first() as? AVMetadataMachineReadableCodeObject ?: return null
        return code.stringValue?.takeIf { it.isNotBlank() }
    }

    val candidates = ArrayList<QrDetectionCandidate>(metadataObjects.size)
    var firstValue: String? = null
    for (objectCandidate in metadataObjects) {
        val code = objectCandidate as? AVMetadataMachineReadableCodeObject ?: continue
        val value = code.stringValue?.takeIf { it.isNotBlank() } ?: continue
        if (firstValue == null) firstValue = value
        val candidate = code.bounds.useContents {
            val left = origin.x.toFloat()
            val top = origin.y.toFloat()
            val width = size.width.toFloat()
            val height = size.height.toFloat()
            QrDetectionCandidate(
                value = value,
                left = left,
                top = top,
                right = left + width,
                bottom = top + height
            )
        }
        candidates.add(candidate)
    }

    if (candidates.isEmpty()) return firstValue
    return pickPreferredQrValue(candidates, frameWidth = 1f, frameHeight = 1f)
}

private const val ZOOM_FACTOR_EPSILON = 0.01
