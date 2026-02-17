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
    private var started = false
    private var paused: Boolean = true
    private var configured = false
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var currentDevice: AVCaptureDevice? = null
    private var lifecycleObserver: NSObjectProtocol? = null
    private var subjectAreaObserver: NSObjectProtocol? = null

    private var lastEmittedValue: String? = null
    private var desiredZoomFraction: Float = 0f
    private var lastAppliedZoomFactor: Double? = null

    private fun emitIfNew(value: String) {
        if (value == lastEmittedValue) return
        lastEmittedValue = value
        onQrCodeScanned?.let { callback ->
            dispatch_async(dispatch_get_main_queue()) {
                callback(value)
            }
        }
    }

    private val metadataDelegate = MetadataDelegate(
        isPaused = { paused },
        onMetadataObjects = { metadataObjects ->
            val value = selectPreferredMetadataValue(metadataObjects) ?: return@MetadataDelegate
            emitIfNew(value)
        }
    )

    private fun resetLastEmittedValue() {
        lastEmittedValue = null
    }

    override fun start(onQrCodeScanned: (String) -> Unit) {
        this.onQrCodeScanned = onQrCodeScanned
        ensureLifecycleObserver()
        dispatch_async(sessionQueue) {
            started = true
            paused = false
            ensureSessionRunning()
            applyZoomIfNeeded()
        }
    }

    override fun pause() {
        dispatch_async(sessionQueue) {
            if (!started) return@dispatch_async
            paused = true
        }
    }

    override fun resume() {
        dispatch_async(sessionQueue) {
            if (!started) return@dispatch_async
            resetLastEmittedValue()
            paused = false
            ensureSessionRunning()
            applyZoomIfNeeded()
        }
    }

    override fun stop() {
        removeLifecycleObserver()
        dispatch_async(sessionQueue) {
            started = false
            paused = true
            if (session.running) {
                session.stopRunning()
            }
            removeSubjectAreaObserver()
            teardownSession()
            configured = false
            currentDevice = null
            lastAppliedZoomFactor = null
            desiredZoomFraction = 0f
            resetLastEmittedValue()
        }
        dispatch_async(dispatch_get_main_queue()) {
            previewLayer?.removeFromSuperlayer()
            previewLayer = null
        }
        onQrCodeScanned = null
    }

    override fun bindPreview(surface: CameraPreviewSurface) {
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
        }
    }

    override fun unbindPreview() {
        dispatch_async(dispatch_get_main_queue()) {
            previewLayer?.removeFromSuperlayer()
        }
        dispatch_async(sessionQueue) {
            val previousRequestedZoom = desiredZoomFraction
            desiredZoomFraction = 0f
            applyZoomIfNeeded(previousRequestedZoom)
        }
    }

    override fun setZoom(zoomFraction: Float) {
        dispatch_async(sessionQueue) {
            val previousRequestedZoom = desiredZoomFraction
            desiredZoomFraction = zoomFraction.coerceIn(0f, 1f)
            applyZoomIfNeeded(previousRequestedZoom)
        }
    }

    private fun ensureLifecycleObserver() {
        if (lifecycleObserver != null) return
        lifecycleObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            dispatch_async(sessionQueue) {
                if (started && !paused) {
                    ensureSessionRunning()
                }
            }
        }
    }

    private fun removeLifecycleObserver() {
        lifecycleObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        lifecycleObserver = null
    }

    private fun installSubjectAreaObserver(device: AVCaptureDevice) {
        if (subjectAreaObserver != null) return
        subjectAreaObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVCaptureDeviceSubjectAreaDidChangeNotification,
            `object` = device,
            queue = null
        ) { _ ->
            dispatch_async(sessionQueue) {
                if (!started || paused || currentDevice !== device) return@dispatch_async
                runAutofocusPulse(device)
            }
        }
    }

    private fun removeSubjectAreaObserver() {
        subjectAreaObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        subjectAreaObserver = null
    }

    private fun ensureSessionRunning() {
        configureSessionIfNeeded()
        if (!configured) return
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
                device.subjectAreaChangeMonitoringEnabled = true
                device.unlockForConfiguration()
            }
        }
    }

    private fun teardownSession() {
        session.beginConfiguration()
        try {
            removeAllInputsAndOutputs()
        } finally {
            session.commitConfiguration()
        }
    }

    private fun removeAllInputsAndOutputs() {
        @Suppress("UNCHECKED_CAST")
        val inputs = session.inputs as? List<AVCaptureInput> ?: emptyList()
        for (input in inputs) {
            session.removeInput(input)
        }

        @Suppress("UNCHECKED_CAST")
        val outputs = session.outputs as? List<AVCaptureOutput> ?: emptyList()
        for (output in outputs) {
            session.removeOutput(output)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun configureSessionIfNeeded() {
        if (configured) return

        session.beginConfiguration()
        var success = false
        try {
            removeAllInputsAndOutputs()
            removeSubjectAreaObserver()

            val device = selectDevice()
            if (device == null) {
                session.sessionPreset = AVCaptureSessionPresetHigh
                NSLog("Failed to acquire capture device for QR scanning")
                return
            }
            currentDevice = device

            val customFormatSet = configureDeviceFormat(device)
            session.sessionPreset = if (customFormatSet) {
                AVCaptureSessionPresetInputPriority
            } else {
                AVCaptureSessionPresetHigh
            }

            val input = createDeviceInput(device)
            if (input == null || !session.canAddInput(input)) {
                NSLog("Unable to add camera input to capture session")
                return
            }
            session.addInput(input)

            val metadataOutput = AVCaptureMetadataOutput()
            if (!session.canAddOutput(metadataOutput)) {
                NSLog("Unable to add metadata output to capture session")
                return
            }
            session.addOutput(metadataOutput)
            metadataOutput.setMetadataObjectsDelegate(metadataDelegate, sessionQueue)
            metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            installSubjectAreaObserver(device)

            success = true
        } finally {
            session.commitConfiguration()
        }

        configured = success
        if (!success) {
            currentDevice = null
            lastAppliedZoomFactor = null
        }
    }

    private fun selectDevice(): AVCaptureDevice? {
        val ultraWide = AVCaptureDevice.defaultDeviceWithDeviceType(
            deviceType = AVCaptureDeviceTypeBuiltInUltraWideCamera,
            mediaType = AVMediaTypeVideo,
            position = AVCaptureDevicePositionBack
        )
        if (ultraWide != null) return ultraWide

        val wide = AVCaptureDevice.defaultDeviceWithDeviceType(
            deviceType = AVCaptureDeviceTypeBuiltInWideAngleCamera,
            mediaType = AVMediaTypeVideo,
            position = AVCaptureDevicePositionBack
        )
        if (wide != null) return wide

        return AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun configureDeviceFormat(device: AVCaptureDevice): Boolean {
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

            @Suppress("UNCHECKED_CAST")
            val fpsRanges = format.videoSupportedFrameRateRanges as? List<AVFrameRateRange>
                ?: continue
            val supportsTargetFps = fpsRanges.any { it.maxFrameRate >= targetFps }
            if (!supportsTargetFps) continue

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

    @OptIn(ExperimentalForeignApi::class)
    private fun applyZoomIfNeeded(previousRequestedZoom: Float = desiredZoomFraction) {
        val device = currentDevice ?: return
        memScoped {
            val minZoom = 1.0
            val maxZoom =
                (
                    device.activeFormat.valueForKey(
                        "videoMaxZoomFactor"
                    ) as? NSNumber
                    )?.doubleValue
                    ?: 4.0
            val target = minZoom * (maxZoom / minZoom).pow(desiredZoomFraction.toDouble())
            val clampedTarget = target.coerceIn(minZoom, maxZoom)
            val previous = lastAppliedZoomFactor
            if (previous != null && abs(previous - clampedTarget) < ZOOM_FACTOR_EPSILON) {
                return@memScoped
            }
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            var shouldRestoreContinuous = false
            if (device.lockForConfiguration(errorPtr.ptr)) {
                device.videoZoomFactor = clampedTarget
                val shouldPulseFocus = shouldRunAutofocusPulse(
                    previousRequestedZoom = previousRequestedZoom,
                    requestedZoom = desiredZoomFraction
                )
                if (shouldPulseFocus) {
                    if (device.isFocusModeSupported(AVCaptureFocusModeAutoFocus)) {
                        device.focusMode = AVCaptureFocusModeAutoFocus
                    }
                    if (device.isExposureModeSupported(AVCaptureExposureModeAutoExpose)) {
                        device.exposureMode = AVCaptureExposureModeAutoExpose
                    }
                    shouldRestoreContinuous = true
                }
                device.subjectAreaChangeMonitoringEnabled = true
                lastAppliedZoomFactor = clampedTarget
                device.unlockForConfiguration()
            }
            if (shouldRestoreContinuous) {
                scheduleContinuousFocusAndExposureRestore(device)
            }
        }
    }

    private fun shouldRunAutofocusPulse(
        previousRequestedZoom: Float,
        requestedZoom: Float
    ): Boolean {
        val snappedBackToDefault =
            previousRequestedZoom >= ZOOM_PULSE_FROM_THRESHOLD &&
                requestedZoom <= ZOOM_DEFAULT_THRESHOLD
        val largeZoomJump = abs(previousRequestedZoom - requestedZoom) >= ZOOM_PULSE_DELTA_THRESHOLD
        return snappedBackToDefault || largeZoomJump
    }

    private fun runAutofocusPulse(device: AVCaptureDevice) {
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            var shouldRestoreContinuous = false
            if (device.lockForConfiguration(errorPtr.ptr)) {
                if (device.isFocusModeSupported(AVCaptureFocusModeAutoFocus)) {
                    device.focusMode = AVCaptureFocusModeAutoFocus
                }
                if (device.isExposureModeSupported(AVCaptureExposureModeAutoExpose)) {
                    device.exposureMode = AVCaptureExposureModeAutoExpose
                }
                device.subjectAreaChangeMonitoringEnabled = true
                shouldRestoreContinuous = true
                device.unlockForConfiguration()
            }
            if (shouldRestoreContinuous) {
                scheduleContinuousFocusAndExposureRestore(device)
            }
        }
    }

    private fun scheduleContinuousFocusAndExposureRestore(device: AVCaptureDevice) {
        val delayNanos = REFOCUS_RESTORE_DELAY_MS * NSEC_PER_MSEC.toLong()
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, delayNanos),
            sessionQueue
        ) {
            if (currentDevice !== device) return@dispatch_after
            resetFocusAndExposure()
        }
    }
}

private class MetadataDelegate(
    private val isPaused: () -> Boolean,
    private val onMetadataObjects: (List<*>) -> Unit
) : NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        if (isPaused()) return
        onMetadataObjects(didOutputMetadataObjects)
    }
}

private fun selectPreferredMetadataValue(metadataObjects: List<*>): String? {
    if (metadataObjects.isEmpty()) return null
    if (metadataObjects.size == 1) {
        val code = metadataObjects.first() as? AVMetadataMachineReadableCodeObject ?: return null
        return code.stringValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    val candidates = ArrayList<QrDetectionCandidate>(metadataObjects.size)
    var firstValue: String? = null
    for (objectCandidate in metadataObjects) {
        val code = objectCandidate as? AVMetadataMachineReadableCodeObject ?: continue
        val value = code.stringValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: continue
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
    return pickPreferredQrValue(
        candidates = candidates,
        frameWidth = 1f,
        frameHeight = 1f
    )
}

private const val ZOOM_FACTOR_EPSILON = 0.01
private const val ZOOM_PULSE_FROM_THRESHOLD = 0.12f
private const val ZOOM_DEFAULT_THRESHOLD = 0.02f
private const val ZOOM_PULSE_DELTA_THRESHOLD = 0.2f
private const val REFOCUS_RESTORE_DELAY_MS = 220L
