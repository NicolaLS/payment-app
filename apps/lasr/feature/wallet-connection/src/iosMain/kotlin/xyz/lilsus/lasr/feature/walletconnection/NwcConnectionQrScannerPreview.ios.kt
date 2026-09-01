@file:OptIn(
    kotlinx.cinterop.BetaInteropApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class
)

package xyz.lilsus.lasr.feature.walletconnection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.math.abs
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDeviceMeta
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInDualWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInTripleCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInUltraWideCamera
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureExposureModeContinuousAutoExposure
import platform.AVFoundation.AVCaptureFocusModeContinuousAutoFocus
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureSessionPresetInputPriority
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVFrameRateRange
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.deviceType
import platform.AVFoundation.exposureMode
import platform.AVFoundation.focusMode
import platform.AVFoundation.isExposureModeSupported
import platform.AVFoundation.isFocusModeSupported
import platform.AVFoundation.maxAvailableVideoZoomFactor
import platform.AVFoundation.minAvailableVideoZoomFactor
import platform.AVFoundation.videoZoomFactor
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

@Composable
internal actual fun NwcConnectionQrScannerPreview(
    onQrCodeScanned: (String) -> Unit,
    onCameraPermissionMissing: () -> Unit,
    modifier: Modifier
) {
    val currentOnQrCodeScanned = rememberUpdatedState(onQrCodeScanned)
    val currentOnCameraPermissionMissing = rememberUpdatedState(onCameraPermissionMissing)
    val previewView = remember { NwcCameraPreviewView() }
    val scanner = remember(previewView) { NwcIosPreviewScanner(previewView) }

    DisposableEffect(scanner) {
        scanner.start(
            onQrCodeScanned = { currentOnQrCodeScanned.value(it) },
            onCameraPermissionMissing = { currentOnCameraPermissionMissing.value() }
        )
        onDispose(scanner::stop)
    }

    UIKitView(
        factory = { previewView },
        modifier = modifier,
        properties = UIKitInteropProperties()
    )
}

private class NwcCameraPreviewView : UIView(frame = CGRectZero.readValue()) {
    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    fun attach(session: AVCaptureSession) {
        val layer = AVCaptureVideoPreviewLayer.layerWithSession(session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
        previewLayer?.removeFromSuperlayer()
        previewLayer = layer
        this.layer.addSublayer(layer)
        setNeedsLayout()
        layoutIfNeeded()
    }

    fun detach() {
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val layer = previewLayer ?: return
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        layer.frame = bounds
        CATransaction.commit()
    }
}

private class NwcIosPreviewScanner(private val previewView: NwcCameraPreviewView) {
    private val session = AVCaptureSession()
    private val sessionQueue: dispatch_queue_t = dispatch_queue_create(
        "xyz.lilsus.lasr.nwc.qr.session",
        null
    )
    private val metadataDelegate = NwcMetadataDelegate(::handleMetadataObjects)
    private var onQrCodeScanned: ((String) -> Unit)? = null
    private var lifecycleObserver: NSObjectProtocol? = null
    private var configured = false
    private var running = false
    private var generation = 0L
    private var lastValue: String? = null

    fun start(onQrCodeScanned: (String) -> Unit, onCameraPermissionMissing: () -> Unit) {
        if (!isCameraAuthorized()) {
            onCameraPermissionMissing()
            return
        }
        if (running) return
        this.onQrCodeScanned = onQrCodeScanned
        running = true
        generation += 1
        val currentGeneration = generation
        lastValue = null
        previewView.attach(session)
        ensureLifecycleObserver()
        dispatch_async(sessionQueue) {
            if (!isCurrent(currentGeneration)) return@dispatch_async
            configured = configured || configureSession()
            if (!configured || !isCurrent(currentGeneration)) {
                reportUnavailable(currentGeneration)
                return@dispatch_async
            }
            if (!session.running) session.startRunning()
        }
    }

    fun stop() {
        generation += 1
        running = false
        onQrCodeScanned = null
        removeLifecycleObserver()
        previewView.detach()
        dispatch_async(sessionQueue) {
            if (session.running) session.stopRunning()
            teardownSession()
            configured = false
            lastValue = null
        }
    }

    private fun configureSession(): Boolean {
        val selection = selectScannerCamera() ?: return false
        val device = selection.device
        val input = createDeviceInput(device) ?: return false
        val output = AVCaptureMetadataOutput()
        session.beginConfiguration()
        return try {
            removeAllInputsAndOutputs()
            val preferredFormatApplied = applyPreferredCaptureFormat(
                device = device,
                target = selection.formatTarget
            )
            val sessionPreset = if (preferredFormatApplied) {
                AVCaptureSessionPresetInputPriority
            } else {
                AVCaptureSessionPresetHigh
            }
            if (session.canSetSessionPreset(sessionPreset)) {
                session.sessionPreset = sessionPreset
            }
            if (!session.canAddInput(input)) return false
            session.addInput(input)
            if (!session.canAddOutput(output)) return false
            session.addOutput(output)
            output.setMetadataObjectsDelegate(metadataDelegate, sessionQueue)
            @Suppress("UNCHECKED_CAST")
            val availableTypes = output.availableMetadataObjectTypes as? List<Any> ?: emptyList()
            if (availableTypes.none { it == AVMetadataObjectTypeQRCode }) return false
            output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
            applyFixedCameraControls(device)
            true
        } finally {
            session.commitConfiguration()
        }
    }

    private fun createDeviceInput(device: AVCaptureDevice): AVCaptureDeviceInput? = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error.ptr)
        error.value?.let { failure ->
            NSLog("AVCaptureDeviceInput error: ${failure.localizedDescription}")
            return@memScoped null
        }
        input
    }

    private fun handleMetadataObjects(metadataObjects: List<*>) {
        if (!running) return
        val value = metadataObjects.firstNotNullOfOrNull { candidate ->
            val code = candidate as? AVMetadataMachineReadableCodeObject
                ?: return@firstNotNullOfOrNull null
            code.stringValue?.trim()?.takeIf(String::isNotEmpty)
        } ?: return
        if (value == lastValue) return
        lastValue = value
        val currentGeneration = generation
        dispatch_async(dispatch_get_main_queue()) {
            if (isCurrent(currentGeneration)) onQrCodeScanned?.invoke(value)
        }
    }

    private fun reportUnavailable(currentGeneration: Long) {
        if (!isCurrent(currentGeneration)) return
        running = false
        if (session.running) session.stopRunning()
        teardownSession()
        configured = false
        lastValue = null
        dispatch_async(dispatch_get_main_queue()) {
            if (generation == currentGeneration) {
                removeLifecycleObserver()
                previewView.detach()
            }
        }
    }

    private fun ensureLifecycleObserver() {
        if (lifecycleObserver != null) return
        lifecycleObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            val currentGeneration = generation
            dispatch_async(sessionQueue) {
                if (isCurrent(currentGeneration) && configured && !session.running) {
                    session.startRunning()
                }
            }
        }
    }

    private fun removeLifecycleObserver() {
        lifecycleObserver?.let(NSNotificationCenter.defaultCenter::removeObserver)
        lifecycleObserver = null
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
        inputs.forEach(session::removeInput)
        @Suppress("UNCHECKED_CAST")
        val outputs = session.outputs as? List<AVCaptureOutput> ?: emptyList()
        outputs.forEach(session::removeOutput)
    }

    private fun isCurrent(value: Long): Boolean = running && generation == value
}

private class NwcMetadataDelegate(private val onMetadataObjects: (List<*>) -> Unit) :
    NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        onMetadataObjects(didOutputMetadataObjects)
    }
}

private fun isCameraAuthorized(): Boolean =
    AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
        AVAuthorizationStatusAuthorized

private data class ScannerCameraSelection(
    val device: AVCaptureDevice,
    val formatTarget: CaptureFormatTarget
)

private data class CaptureFormatTarget(
    val targetAspectRatio: Double,
    val preferredPixels: Int,
    val minimumPixels: Int,
    val maximumPixels: Int
)

private data class CaptureFormatCandidate(
    val format: AVCaptureDeviceFormat,
    val width: Int,
    val height: Int,
    val maximumFrameRate: Double
) {
    val pixelCount: Int
        get() = width * height
    val aspectRatio: Double
        get() = width.toDouble() / height.toDouble()
}

private fun selectScannerCamera(): ScannerCameraSelection? {
    val discovery = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
        deviceTypes = SCANNER_CAMERA_DEVICE_TYPES,
        mediaType = AVMediaTypeVideo,
        position = AVCaptureDevicePositionBack
    )

    @Suppress("UNCHECKED_CAST")
    val devices = discovery.devices as? List<AVCaptureDevice> ?: emptyList()
    val virtualWide = devices.firstOrNull {
        it.deviceType == AVCaptureDeviceTypeBuiltInDualWideCamera
    } ?: devices.firstOrNull {
        it.deviceType == AVCaptureDeviceTypeBuiltInTripleCamera
    }
    val ultraWide = devices.firstOrNull {
        it.deviceType == AVCaptureDeviceTypeBuiltInUltraWideCamera
    }
    val wide = devices.firstOrNull {
        it.deviceType == AVCaptureDeviceTypeBuiltInWideAngleCamera
    }

    return when {
        virtualWide != null -> ScannerCameraSelection(virtualWide, FOUR_THREE_CAPTURE_TARGET)

        ultraWide != null -> ScannerCameraSelection(ultraWide, FOUR_THREE_CAPTURE_TARGET)

        wide != null -> ScannerCameraSelection(wide, WIDE_FALLBACK_CAPTURE_TARGET)

        else -> AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)?.let {
            ScannerCameraSelection(it, WIDE_FALLBACK_CAPTURE_TARGET)
        }
    }
}

private fun applyPreferredCaptureFormat(
    device: AVCaptureDevice,
    target: CaptureFormatTarget
): Boolean {
    @Suppress("UNCHECKED_CAST")
    val formats = device.formats as? List<AVCaptureDeviceFormat> ?: return false
    val candidates = formats.mapNotNull { format ->
        val description = format.formatDescription ?: return@mapNotNull null
        val dimensions = CMVideoFormatDescriptionGetDimensions(description)
        val width = dimensions.useContents { width }
        val height = dimensions.useContents { height }
        if (width <= 0 || height <= 0) return@mapNotNull null
        @Suppress("UNCHECKED_CAST")
        val frameRateRanges = format.videoSupportedFrameRateRanges as? List<AVFrameRateRange>
            ?: return@mapNotNull null
        val maximumFrameRate = frameRateRanges.maxOfOrNull { it.maxFrameRate }
            ?: return@mapNotNull null
        if (maximumFrameRate < TARGET_CAPTURE_FPS) return@mapNotNull null
        CaptureFormatCandidate(format, width, height, maximumFrameRate)
    }
    val comparator = compareBy<CaptureFormatCandidate> {
        abs(it.aspectRatio - target.targetAspectRatio)
    }.thenBy {
        abs(it.pixelCount - target.preferredPixels)
    }.thenByDescending(CaptureFormatCandidate::pixelCount)
        .thenByDescending(CaptureFormatCandidate::maximumFrameRate)
    val aspectMatches: (CaptureFormatCandidate) -> Boolean = {
        abs(it.aspectRatio - target.targetAspectRatio) <= MAXIMUM_ASPECT_DELTA
    }
    val ranked = linkedSetOf<CaptureFormatCandidate>()
    ranked += candidates.filter {
        it.pixelCount in target.minimumPixels..target.maximumPixels && aspectMatches(it)
    }.sortedWith(comparator)
    ranked += candidates.filter {
        it.pixelCount >= target.minimumPixels && aspectMatches(it)
    }.sortedWith(comparator)
    ranked += candidates.filter(aspectMatches).sortedWith(comparator)

    return ranked.any { candidate -> applyCaptureFormat(device, candidate) }
}

private fun applyCaptureFormat(
    device: AVCaptureDevice,
    candidate: CaptureFormatCandidate
): Boolean = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    if (!device.lockForConfiguration(error.ptr)) return@memScoped false
    try {
        val frameDuration = CMTimeMake(
            value = 1,
            timescale = TARGET_CAPTURE_FPS.toInt()
        )
        device.activeFormat = candidate.format
        device.activeVideoMinFrameDuration = frameDuration
        device.activeVideoMaxFrameDuration = frameDuration
        true
    } finally {
        device.unlockForConfiguration()
    }
}

private fun applyFixedCameraControls(device: AVCaptureDevice) = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    if (!device.lockForConfiguration(error.ptr)) return@memScoped
    try {
        device.videoZoomFactor = 1.0.coerceIn(
            device.minAvailableVideoZoomFactor,
            device.maxAvailableVideoZoomFactor
        )
        if (device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)) {
            device.focusMode = AVCaptureFocusModeContinuousAutoFocus
        }
        if (device.isExposureModeSupported(AVCaptureExposureModeContinuousAutoExposure)) {
            device.exposureMode = AVCaptureExposureModeContinuousAutoExposure
        }
    } finally {
        device.unlockForConfiguration()
    }
}

private const val TARGET_CAPTURE_FPS = 30.0
private const val MAXIMUM_ASPECT_DELTA = 0.08

private val FOUR_THREE_CAPTURE_TARGET = CaptureFormatTarget(
    targetAspectRatio = 4.0 / 3.0,
    preferredPixels = 1920 * 1440,
    minimumPixels = 1440 * 1080,
    maximumPixels = 2304 * 1728
)

private val WIDE_FALLBACK_CAPTURE_TARGET = CaptureFormatTarget(
    targetAspectRatio = 16.0 / 9.0,
    preferredPixels = 2560 * 1440,
    minimumPixels = 1920 * 1080,
    maximumPixels = 3072 * 1728
)

private val SCANNER_CAMERA_DEVICE_TYPES = listOf(
    AVCaptureDeviceTypeBuiltInDualWideCamera,
    AVCaptureDeviceTypeBuiltInTripleCamera,
    AVCaptureDeviceTypeBuiltInWideAngleCamera,
    AVCaptureDeviceTypeBuiltInUltraWideCamera
)
