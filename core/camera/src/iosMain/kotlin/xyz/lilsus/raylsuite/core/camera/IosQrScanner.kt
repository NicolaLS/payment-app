@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package xyz.lilsus.raylsuite.core.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.AVFoundation.*
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.Foundation.*
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.*
import platform.darwin.*

@Composable
actual fun rememberQrScannerController(): QrScannerController = remember {
    IosQrScannerController()
}

actual class CameraPreviewSurface internal constructor(val view: UIView) {
    init {
        view.clipsToBounds = true
        view.userInteractionEnabled = false
    }

    internal fun attachPreviewLayer(layer: AVCaptureVideoPreviewLayer) {
        (view as CameraPreviewView).attachPreviewLayer(layer)
    }

    internal fun detachPreviewLayer() {
        (view as CameraPreviewView).detachPreviewLayer()
    }
}

private class CameraPreviewView(private val onPreviewStreamingChanged: (Boolean) -> Unit) :
    UIView(frame = CGRectZero.readValue()) {
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var streaming = false

    fun attachPreviewLayer(layer: AVCaptureVideoPreviewLayer) {
        previewLayer?.removeFromSuperlayer()
        previewLayer = layer
        layer.removeFromSuperlayer()
        this.layer.addSublayer(layer)
        setStreaming(false)
        setNeedsLayout()
        layoutIfNeeded()
        reportStreamingIfReady()
        dispatch_async(dispatch_get_main_queue()) {
            reportStreamingIfReady()
        }
    }

    fun detachPreviewLayer() {
        previewLayer?.removeFromSuperlayer()
        previewLayer = null
        setStreaming(false)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val layer = previewLayer ?: return
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        layer.frame = bounds
        CATransaction.commit()
        reportStreamingIfReady()
    }

    private fun reportStreamingIfReady() {
        val layer = previewLayer ?: return
        if (hasDrawableBounds() && layer.isPreviewing()) {
            setStreaming(true)
        }
    }

    private fun hasDrawableBounds(): Boolean = bounds.useContents {
        size.width > 0.0 && size.height > 0.0
    }

    private fun setStreaming(value: Boolean) {
        if (streaming == value) return
        streaming = value
        onPreviewStreamingChanged(value)
    }
}

@Composable
actual fun CameraPreviewHost(
    controller: QrScannerController,
    visible: Boolean,
    modifier: Modifier,
    preferCompatibleMode: Boolean,
    onPreviewStreamingChanged: (Boolean) -> Unit
) {
    val currentStreamingCallback = rememberUpdatedState(onPreviewStreamingChanged)
    val surface = remember {
        CameraPreviewSurface(
            CameraPreviewView { streaming -> currentStreamingCallback.value(streaming) }
        )
    }

    DisposableEffect(controller, surface, visible) {
        if (visible) {
            controller.bindPreview(surface)
        } else {
            controller.unbindPreview()
            onPreviewStreamingChanged(false)
        }
        onDispose {
            controller.unbindPreview()
            onPreviewStreamingChanged(false)
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
        platform.AVFoundation.AVAuthorizationStatusAuthorized

private class IosQrScannerController : QrScannerController {
    private val session = AVCaptureSession()
    private val sessionQueue: dispatch_queue_t = dispatch_queue_create(
        "xyz.lilsus.raylsuite.qr.session",
        null
    )
    private val availableBackCameras by lazy(::discoverBackCameras)

    override val supportsManualModeSelection: Boolean
        get() = availableBackCameras.supportsManualModeSelection

    private var onQrCodeScanned: ((String) -> Unit)? = null
    private var onScannerUnavailable: (() -> Unit)? = null
    private var started = false
    private var paused = true
    private var configured = false
    private var desiredScanMode = QrScannerMode.Near
    private var configuredScanMode: QrScannerMode? = null
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var previewSurface: CameraPreviewSurface? = null
    private var previewBound = false
    private var activeDevice: AVCaptureDevice? = null
    private var zoomDevice: AVCaptureDevice? = null
    private var zoomBaseFactor = DEFAULT_BASE_ZOOM_FACTOR
    private var captureConfigurationSummary = "QR scanner configuration: mode=unconfigured"
    private var lifecycleObserver: NSObjectProtocol? = null
    private var subjectAreaObserver: NSObjectProtocol? = null
    private var lastEmittedValue: String? = null
    private var desiredZoomFraction = 0f
    private var lastAppliedZoomFactor: Double? = null

    private val metadataDelegate = MetadataDelegate(
        isPaused = { paused },
        onMetadataObjects = { metadataObjects ->
            val value = selectPreferredMetadataValue(metadataObjects) ?: return@MetadataDelegate
            emitIfNew(value)
        }
    )

    override fun start(
        onQrCodeScanned: (String) -> Unit,
        onCameraPermissionMissing: () -> Unit,
        onScannerUnavailable: () -> Unit
    ): Boolean {
        if (!isCameraAuthorized()) {
            onCameraPermissionMissing()
            return false
        }
        this.onQrCodeScanned = onQrCodeScanned
        this.onScannerUnavailable = onScannerUnavailable
        ensureLifecycleObserver()
        dispatch_async(sessionQueue) {
            started = true
            paused = false
            resetLastEmittedValue()
            ensureSessionRunning()
            applyZoomIfNeeded()
        }
        return true
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
            configuredScanMode = null
            clearConfiguredDevice()
            lastAppliedZoomFactor = null
            desiredZoomFraction = 0f
            resetLastEmittedValue()
        }
        dispatch_async(dispatch_get_main_queue()) {
            previewSurface?.detachPreviewLayer()
            previewSurface = null
            previewLayer?.removeFromSuperlayer()
            previewLayer = null
        }
        onQrCodeScanned = null
        onScannerUnavailable = null
    }

    override fun bindPreview(surface: CameraPreviewSurface) {
        previewBound = true
        dispatch_async(dispatch_get_main_queue()) {
            if (!previewBound) return@dispatch_async
            val layer = previewLayer ?: AVCaptureVideoPreviewLayer.layerWithSession(session).apply {
                videoGravity = AVLayerVideoGravityResizeAspectFill
                connection?.videoOrientation = AVCaptureVideoOrientationPortrait
                previewLayer = this
            }
            previewSurface?.detachPreviewLayer()
            previewSurface = surface
            surface.attachPreviewLayer(layer)
        }
    }

    override fun unbindPreview() {
        previewBound = false
        dispatch_async(dispatch_get_main_queue()) {
            previewSurface?.detachPreviewLayer()
            previewSurface = null
        }
        dispatch_async(sessionQueue) {
            val previousRequestedZoom = desiredZoomFraction
            desiredZoomFraction = 0f
            applyZoomIfNeeded(previousRequestedZoom)
        }
    }

    override fun setMode(mode: QrScannerMode) {
        dispatch_async(sessionQueue) {
            if (desiredScanMode == mode) return@dispatch_async
            desiredScanMode = mode
            resetLastEmittedValue()
            if (configured || started) {
                configureSessionIfNeeded(force = true)
                if (!configured) {
                    reportScannerUnavailable()
                    return@dispatch_async
                }
                if (!paused) {
                    applyZoomIfNeeded()
                    activeDevice?.let { device ->
                        runFocusRecovery(
                            device = device,
                            centered = desiredScanMode == QrScannerMode.Far
                        )
                    }
                }
            }
        }
    }

    override fun setZoom(zoomFraction: Float) {
        dispatch_async(sessionQueue) {
            val previousRequestedZoom = desiredZoomFraction
            desiredZoomFraction = zoomFraction.coerceIn(0f, 1f)
            applyZoomIfNeeded(
                previousRequestedZoom = previousRequestedZoom,
                smooth = previewBound
            )
        }
    }

    private fun emitIfNew(value: String) {
        if (value == lastEmittedValue) return
        lastEmittedValue = value
        onQrCodeScanned?.let { callback ->
            dispatch_async(dispatch_get_main_queue()) {
                callback(value)
            }
        }
    }

    private fun resetLastEmittedValue() {
        lastEmittedValue = null
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
                if (!started || paused || zoomDevice !== device) return@dispatch_async
                runAutofocusPulse(
                    device = device,
                    centered = desiredScanMode == QrScannerMode.Far
                )
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
        configureSessionIfNeeded(force = configuredScanMode != desiredScanMode)
        if (!configured) {
            reportScannerUnavailable()
            return
        }
        if (!session.running && started && !paused) {
            session.startRunning()
        }
        activeDevice?.let { device ->
            val centered = desiredScanMode == QrScannerMode.Far
            resetFocusAndExposure(
                device = device,
                centered = centered
            )
            runFocusRecovery(
                device = device,
                centered = centered
            )
        }
    }

    private fun reportScannerUnavailable() {
        val callback = onScannerUnavailable
        started = false
        paused = true
        if (session.running) {
            session.stopRunning()
        }
        removeSubjectAreaObserver()
        teardownSession()
        configured = false
        configuredScanMode = null
        clearConfiguredDevice()
        lastAppliedZoomFactor = null
        desiredZoomFraction = 0f
        resetLastEmittedValue()
        dispatch_async(dispatch_get_main_queue()) {
            removeLifecycleObserver()
            previewSurface?.detachPreviewLayer()
            previewSurface = null
            previewLayer?.removeFromSuperlayer()
            previewLayer = null
            callback?.invoke()
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun resetFocusAndExposure() {
        val device = activeDevice ?: return
        resetFocusAndExposure(
            device = device,
            centered = desiredScanMode == QrScannerMode.Far
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun resetFocusAndExposure(device: AVCaptureDevice, centered: Boolean) {
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            if (device.lockForConfiguration(errorPtr.ptr)) {
                if (centered) {
                    applyCenterFocusAndExposurePoint(device)
                }
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

    private fun clearConfiguredDevice() {
        activeDevice = null
        zoomDevice = null
        zoomBaseFactor = DEFAULT_BASE_ZOOM_FACTOR
        captureConfigurationSummary = "QR scanner configuration: mode=unconfigured"
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

    private fun configureSessionIfNeeded(force: Boolean = false) {
        if (configured && !force && configuredScanMode == desiredScanMode) return

        val configuration = selectCameraConfiguration(desiredScanMode)
        if (configured && configuration != null && activeDevice === configuration.device) {
            configuredScanMode = desiredScanMode
            zoomBaseFactor = configuration.baseZoomFactor
            captureConfigurationSummary = buildReusedCameraSummary(configuration)
            NSLog(captureConfigurationSummary)
            return
        }

        val wasRunning = session.running
        if (wasRunning) {
            session.stopRunning()
        }

        session.beginConfiguration()
        var success = false
        var selectedConfiguration: SelectedCameraConfiguration? = null
        try {
            removeAllInputsAndOutputs()
            removeSubjectAreaObserver()
            clearConfiguredDevice()

            selectedConfiguration = configuration
            success = configureSingleCameraSession(configuration)
        } finally {
            session.commitConfiguration()
        }

        configured = success
        configuredScanMode = if (success) desiredScanMode else null
        if (!success) {
            clearConfiguredDevice()
            lastAppliedZoomFactor = null
            captureConfigurationSummary = buildConfigurationFailureSummary(selectedConfiguration)
        }

        if (success && wasRunning && started && !paused) {
            session.startRunning()
        }
        NSLog(captureConfigurationSummary)
    }

    private fun discoverBackCameras(): BackCameraDiscoveryResult {
        val discoverySession = AVCaptureDeviceDiscoverySession.discoverySessionWithDeviceTypes(
            deviceTypes = BACK_CAMERA_DEVICE_TYPES,
            mediaType = AVMediaTypeVideo,
            position = AVCaptureDevicePositionBack
        )

        @Suppress("UNCHECKED_CAST")
        val devices = discoverySession.devices as? List<AVCaptureDevice> ?: emptyList()
        val dualWide = devices.firstOrNull {
            it.deviceType == AVCaptureDeviceTypeBuiltInDualWideCamera
        }
        val triple = devices.firstOrNull {
            it.deviceType == AVCaptureDeviceTypeBuiltInTripleCamera
        }
        val wide = devices.firstOrNull {
            it.deviceType == AVCaptureDeviceTypeBuiltInWideAngleCamera
        }
        val ultraWide = devices.firstOrNull {
            it.deviceType == AVCaptureDeviceTypeBuiltInUltraWideCamera
        }
        NSLog(
            "Back camera discovery: dualWide=${dualWide?.localizedName ?: "none"}, " +
                "triple=${triple?.localizedName ?: "none"}, " +
                "wide=${wide?.localizedName ?: "none"}, " +
                "ultraWide=${ultraWide?.localizedName ?: "none"}"
        )
        return BackCameraDiscoveryResult(
            dualWide = dualWide,
            triple = triple,
            wide = wide,
            ultraWide = ultraWide
        )
    }

    private fun selectCameraConfiguration(mode: QrScannerMode): SelectedCameraConfiguration? {
        val discovered = availableBackCameras
        val virtualWide = discovered.dualWide ?: discovered.triple
        val fallbackDevice = discovered.wide
            ?: discovered.ultraWide
            ?: virtualWide
            ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            ?: return null

        return when (mode) {
            QrScannerMode.Near -> when {
                virtualWide != null -> SelectedCameraConfiguration(
                    mode = mode,
                    device = virtualWide,
                    profileName = virtualWide.profileName("ultra-wide-4:3"),
                    profile = ULTRA_WIDE_FORMAT_PROFILE,
                    baseZoomFactor = DEFAULT_BASE_ZOOM_FACTOR
                )

                discovered.ultraWide != null -> SelectedCameraConfiguration(
                    mode = mode,
                    device = discovered.ultraWide,
                    profileName = "ultra-wide-4:3",
                    profile = ULTRA_WIDE_FORMAT_PROFILE,
                    baseZoomFactor = DEFAULT_BASE_ZOOM_FACTOR
                )

                discovered.wide != null -> SelectedCameraConfiguration(
                    mode = mode,
                    device = discovered.wide,
                    profileName = "wide-fallback",
                    profile = WIDE_FORMAT_PROFILE,
                    baseZoomFactor = DEFAULT_BASE_ZOOM_FACTOR,
                    fallbackReason = "ultra-wide camera unavailable"
                )

                else -> SelectedCameraConfiguration(
                    mode = mode,
                    device = fallbackDevice,
                    profileName = "default-fallback",
                    profile = WIDE_FORMAT_PROFILE,
                    baseZoomFactor = DEFAULT_BASE_ZOOM_FACTOR,
                    fallbackReason = "no dedicated back camera discovered"
                )
            }

            QrScannerMode.Far -> when {
                virtualWide != null -> SelectedCameraConfiguration(
                    mode = mode,
                    device = virtualWide,
                    profileName = virtualWide.profileName("virtual-2x"),
                    profile = null,
                    baseZoomFactor = FAR_MODE_BASE_ZOOM_FACTOR
                )

                discovered.wide != null -> SelectedCameraConfiguration(
                    mode = mode,
                    device = discovered.wide,
                    profileName = "wide-16:9-2x",
                    profile = WIDE_FORMAT_PROFILE,
                    baseZoomFactor = FAR_MODE_BASE_ZOOM_FACTOR
                )

                discovered.ultraWide != null -> SelectedCameraConfiguration(
                    mode = mode,
                    device = discovered.ultraWide,
                    profileName = "ultra-wide-fallback",
                    profile = ULTRA_WIDE_FORMAT_PROFILE,
                    baseZoomFactor = DEFAULT_BASE_ZOOM_FACTOR,
                    fallbackReason = "wide camera unavailable"
                )

                else -> SelectedCameraConfiguration(
                    mode = mode,
                    device = fallbackDevice,
                    profileName = "default-fallback",
                    profile = WIDE_FORMAT_PROFILE,
                    baseZoomFactor = FAR_MODE_BASE_ZOOM_FACTOR,
                    fallbackReason = "no dedicated back camera discovered"
                )
            }
        }
    }

    private fun configureSingleCameraSession(configuration: SelectedCameraConfiguration?): Boolean {
        if (configuration == null) {
            applyBestEffortSessionPreset(AVCaptureSessionPresetHigh)
            return false
        }

        val selectedFormat = selectAndApplyFormat(
            device = configuration.device,
            profile = configuration.profile
        )
        val sessionPreset = if (selectedFormat != null) {
            applyBestEffortSessionPreset(AVCaptureSessionPresetInputPriority)
        } else {
            applyBestEffortSessionPreset(AVCaptureSessionPresetHigh)
        }

        val input = createDeviceInput(configuration.device)
        if (input == null || !session.canAddInput(input)) {
            NSLog("Unable to add camera input to capture session")
            return false
        }
        session.addInput(input)

        val metadataOutput = AVCaptureMetadataOutput()
        if (!session.canAddOutput(metadataOutput)) {
            NSLog("Unable to add metadata output to capture session")
            return false
        }
        session.addOutput(metadataOutput)
        if (!configureMetadataOutput(metadataOutput)) {
            NSLog("QR metadata output unavailable for selected camera")
            return false
        }

        activeDevice = configuration.device
        zoomDevice = configuration.device
        zoomBaseFactor = configuration.baseZoomFactor
        zoomDevice?.let(::installSubjectAreaObserver)
        captureConfigurationSummary = buildSingleCameraSummary(
            configuration = configuration,
            selectedFormat = selectedFormat,
            sessionPreset = sessionPreset
        )
        return true
    }

    private fun configureMetadataOutput(metadataOutput: AVCaptureMetadataOutput): Boolean {
        metadataOutput.setMetadataObjectsDelegate(metadataDelegate, sessionQueue)
        @Suppress("UNCHECKED_CAST")
        val availableTypes = metadataOutput.availableMetadataObjectTypes as? List<Any>
            ?: emptyList()
        if (availableTypes.none { it == AVMetadataObjectTypeQRCode }) return false
        metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        return true
    }

    private fun applyBestEffortSessionPreset(preferredPreset: String?): String {
        val presets = listOf(
            preferredPreset,
            AVCaptureSessionPresetHigh,
            AVCaptureSessionPresetMedium,
            AVCaptureSessionPresetLow
        )
            .filterNotNull()
            .distinct()

        for (preset in presets) {
            if (session.canSetSessionPreset(preset)) {
                session.sessionPreset = preset
                return preset
            }
        }

        return session.sessionPreset ?: "unknown"
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun selectAndApplyFormat(
        device: AVCaptureDevice,
        profile: CameraFormatProfile?
    ): CameraFormatCandidate? {
        if (profile == null) return null

        val candidates = collectFormatCandidates(device)
            .filter { candidate -> candidate.maxFrameRate >= MIN_CAPTURE_FPS }
        if (candidates.isEmpty()) return null

        val ranked = rankFormatCandidates(
            candidates = candidates,
            profile = profile
        )
        for (candidate in ranked) {
            if (applyFormatCandidate(device, candidate)) {
                return candidate
            }
        }
        return null
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun collectFormatCandidates(device: AVCaptureDevice): List<CameraFormatCandidate> {
        @Suppress("UNCHECKED_CAST")
        val formats = device.formats as? List<AVCaptureDeviceFormat> ?: return emptyList()
        val candidates = ArrayList<CameraFormatCandidate>(formats.size)

        for (format in formats) {
            val desc = format.formatDescription ?: continue
            val dimensions = CMVideoFormatDescriptionGetDimensions(desc)
            val width = dimensions.useContents { width }
            val height = dimensions.useContents { height }
            if (width <= 0 || height <= 0) continue

            @Suppress("UNCHECKED_CAST")
            val fpsRanges = format.videoSupportedFrameRateRanges as? List<AVFrameRateRange>
                ?: continue
            val maxFrameRate = fpsRanges.maxOfOrNull { it.maxFrameRate } ?: continue

            candidates.add(
                CameraFormatCandidate(
                    format = format,
                    width = width,
                    height = height,
                    maxFrameRate = maxFrameRate
                )
            )
        }
        return candidates
    }

    private fun rankFormatCandidates(
        candidates: List<CameraFormatCandidate>,
        profile: CameraFormatProfile
    ): List<CameraFormatCandidate> {
        val tiers = listOf(
            FormatSelectionTier(
                minimumPixels = profile.minimumPixels,
                enforceAspect = true,
                enforceMaximumPixels = true
            ),
            FormatSelectionTier(
                minimumPixels = profile.minimumPixels,
                enforceAspect = true,
                enforceMaximumPixels = false
            ),
            FormatSelectionTier(
                minimumPixels = 0,
                enforceAspect = true,
                enforceMaximumPixels = false
            )
        )

        val rankingComparator = compareBy<CameraFormatCandidate> {
            abs(it.aspectRatio - profile.targetAspectRatio)
        }.thenBy {
            abs(it.pixelCount - profile.preferredPixels)
        }.thenByDescending {
            it.pixelCount
        }.thenByDescending {
            it.maxFrameRate
        }

        val ranked = linkedSetOf<CameraFormatCandidate>()
        for (tier in tiers) {
            val tierCandidates = candidates
                .asSequence()
                .filter { candidate ->
                    val matchesMinimumPixels = candidate.pixelCount >= tier.minimumPixels
                    val matchesMaximumPixels =
                        !tier.enforceMaximumPixels || candidate.pixelCount <= profile.maximumPixels
                    val matchesAspect =
                        !tier.enforceAspect ||
                            abs(candidate.aspectRatio - profile.targetAspectRatio) <=
                            profile.maximumAspectDelta
                    matchesMinimumPixels && matchesMaximumPixels && matchesAspect
                }
                .sortedWith(rankingComparator)
                .toList()
            ranked.addAll(tierCandidates)
        }
        return ranked.toList()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun applyFormatCandidate(
        device: AVCaptureDevice,
        candidate: CameraFormatCandidate
    ): Boolean = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        if (!device.lockForConfiguration(errorPtr.ptr)) {
            return@memScoped false
        }
        try {
            val targetFps = TARGET_CAPTURE_FPS.coerceAtMost(candidate.maxFrameRate)
            val fpsTimescale = targetFps.toInt().coerceAtLeast(1)
            val frameDuration = CMTimeMake(
                value = 1,
                timescale = fpsTimescale
            )
            device.activeFormat = candidate.format
            device.activeVideoMinFrameDuration = frameDuration
            device.activeVideoMaxFrameDuration = frameDuration
            true
        } finally {
            device.unlockForConfiguration()
        }
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
    private fun applyZoomIfNeeded(
        previousRequestedZoom: Float = desiredZoomFraction,
        smooth: Boolean = false
    ) {
        val device = zoomDevice ?: return
        memScoped {
            val minZoom = maxOf(DEFAULT_BASE_ZOOM_FACTOR, device.minAvailableVideoZoomFactor)
            val maxZoom = maxOf(minZoom, device.maxAvailableVideoZoomFactor)
            val baseZoom = zoomBaseFactor.coerceIn(minZoom, maxZoom)
            val target = if (maxZoom <= baseZoom) {
                baseZoom
            } else {
                baseZoom * (maxZoom / baseZoom).pow(desiredZoomFraction.toDouble())
            }
            val clampedTarget = target.coerceIn(minZoom, maxZoom)
            val previous = lastAppliedZoomFactor
            if (previous != null && abs(previous - clampedTarget) < ZOOM_FACTOR_EPSILON) {
                return@memScoped
            }
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            var shouldRestoreContinuous = false
            if (device.lockForConfiguration(errorPtr.ptr)) {
                if (smooth) {
                    device.rampToVideoZoomFactor(
                        factor = clampedTarget,
                        withRate = ZOOM_RAMP_RATE
                    )
                } else {
                    device.videoZoomFactor = clampedTarget
                }
                val shouldPulseFocus = shouldRunAutofocusPulse(
                    previousRequestedZoom = previousRequestedZoom,
                    requestedZoom = desiredZoomFraction
                )
                if (shouldPulseFocus) {
                    if (desiredScanMode == QrScannerMode.Far) {
                        applyCenterFocusAndExposurePoint(device)
                    }
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

    private fun runAutofocusPulse(device: AVCaptureDevice, centered: Boolean) {
        memScoped {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            var shouldRestoreContinuous = false
            if (device.lockForConfiguration(errorPtr.ptr)) {
                if (centered) {
                    applyCenterFocusAndExposurePoint(device)
                }
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

    private fun runFocusRecovery(device: AVCaptureDevice, centered: Boolean) {
        runAutofocusPulse(
            device = device,
            centered = centered
        )
        scheduleAutofocusPulse(
            device = device,
            delayMillis = SETTLED_REFOCUS_DELAY_MS,
            centered = centered
        )
    }

    private fun scheduleAutofocusPulse(
        device: AVCaptureDevice,
        delayMillis: Long,
        centered: Boolean
    ) {
        val delayNanos = delayMillis * NSEC_PER_MSEC.toLong()
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, delayNanos),
            sessionQueue
        ) {
            if (!started || paused || zoomDevice !== device) return@dispatch_after
            runAutofocusPulse(
                device = device,
                centered = centered
            )
        }
    }

    private fun scheduleContinuousFocusAndExposureRestore(device: AVCaptureDevice) {
        val delayNanos = REFOCUS_RESTORE_DELAY_MS * NSEC_PER_MSEC.toLong()
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, delayNanos),
            sessionQueue
        ) {
            if (zoomDevice !== device) return@dispatch_after
            resetFocusAndExposure(
                device = device,
                centered = desiredScanMode == QrScannerMode.Far
            )
        }
    }

    private fun applyCenterFocusAndExposurePoint(device: AVCaptureDevice) {
        val center = CGPointMake(0.5, 0.5)
        if (device.focusPointOfInterestSupported) {
            device.focusPointOfInterest = center
        }
        if (device.exposurePointOfInterestSupported) {
            device.exposurePointOfInterest = center
        }
    }

    private fun buildSingleCameraSummary(
        configuration: SelectedCameraConfiguration,
        selectedFormat: CameraFormatCandidate?,
        sessionPreset: String
    ): String {
        val formatSummary = selectedFormat?.let(::formatSummary)
            ?: "system-default (preset=$sessionPreset)"
        val fallbackSuffix =
            configuration.fallbackReason?.let { "; fallback=$it" }.orEmpty()
        return "QR scanner configuration: mode=${configuration.mode.name.lowercase()}; " +
            "profile=${configuration.profileName}; device=${deviceSummary(
                configuration.device
            )}; " +
            "format=$formatSummary; targetFps=$TARGET_CAPTURE_FPS; " +
            "baseZoom=${configuration.baseZoomFactor}; ${cameraDiagnostics(
                configuration.device
            )}$fallbackSuffix"
    }

    private fun buildReusedCameraSummary(configuration: SelectedCameraConfiguration): String {
        val fallbackSuffix =
            configuration.fallbackReason?.let { "; fallback=$it" }.orEmpty()
        return "QR scanner configuration: mode=${configuration.mode.name.lowercase()}; " +
            "profile=${configuration.profileName}; device=${deviceSummary(
                configuration.device
            )}; " +
            "reused=true; baseZoom=${configuration.baseZoomFactor}; ${cameraDiagnostics(
                configuration.device
            )}$fallbackSuffix"
    }

    private fun buildConfigurationFailureSummary(
        configuration: SelectedCameraConfiguration?
    ): String {
        val modeSummary = configuration?.mode?.name?.lowercase() ?: "unknown"
        val profileSummary = configuration?.profileName ?: "unconfigured"
        return "QR scanner configuration failed: mode=$modeSummary; profile=$profileSummary"
    }

    private fun deviceSummary(device: AVCaptureDevice): String =
        "${device.localizedName}(${device.uniqueID})"

    private fun formatSummary(candidate: CameraFormatCandidate): String =
        "${candidate.width}x${candidate.height}(maxFps=${candidate.maxFrameRate})"

    private fun cameraDiagnostics(device: AVCaptureDevice): String {
        @Suppress("UNCHECKED_CAST")
        val switchFactors = device.virtualDeviceSwitchOverVideoZoomFactors as? List<NSNumber>
            ?: emptyList()
        val supportsFocus = device.isFocusModeSupported(AVCaptureFocusModeContinuousAutoFocus)
        val supportsExposure = device.isExposureModeSupported(
            AVCaptureExposureModeContinuousAutoExposure
        )
        return "deviceType=${device.deviceType}; " +
            "switchZooms=${switchFactors.joinToString(prefix = "[", postfix = "]")}; " +
            "minFocusMm=${device.minimumFocusDistance}; " +
            "continuousFocus=$supportsFocus; continuousExposure=$supportsExposure"
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

private data class CameraFormatProfile(
    val targetAspectRatio: Double,
    val preferredPixels: Int,
    val minimumPixels: Int,
    val maximumPixels: Int,
    val maximumAspectDelta: Double
)

private data class CameraFormatCandidate(
    val format: AVCaptureDeviceFormat,
    val width: Int,
    val height: Int,
    val maxFrameRate: Double
) {
    val pixelCount: Int
        get() = width * height
    val aspectRatio: Double
        get() = width.toDouble() / height.toDouble()
}

private data class FormatSelectionTier(
    val minimumPixels: Int,
    val enforceAspect: Boolean,
    val enforceMaximumPixels: Boolean
)

private data class BackCameraDiscoveryResult(
    val dualWide: AVCaptureDevice?,
    val triple: AVCaptureDevice?,
    val wide: AVCaptureDevice?,
    val ultraWide: AVCaptureDevice?
) {
    val supportsManualModeSelection: Boolean
        get() = dualWide != null ||
            triple != null ||
            (wide != null && ultraWide != null && wide !== ultraWide)
}

private data class SelectedCameraConfiguration(
    val mode: QrScannerMode,
    val device: AVCaptureDevice,
    val profileName: String,
    val profile: CameraFormatProfile?,
    val baseZoomFactor: Double,
    val fallbackReason: String? = null
)

private const val ZOOM_FACTOR_EPSILON = 0.01
private const val ZOOM_PULSE_FROM_THRESHOLD = 0.12f
private const val ZOOM_DEFAULT_THRESHOLD = 0.02f
private const val ZOOM_PULSE_DELTA_THRESHOLD = 0.2f
private const val ZOOM_RAMP_RATE = 6f

private const val DEFAULT_BASE_ZOOM_FACTOR = 1.0
private const val FAR_MODE_BASE_ZOOM_FACTOR = 2.0

private const val TARGET_CAPTURE_FPS = 30.0
private const val MIN_CAPTURE_FPS = 30.0

private const val ASPECT_RATIO_4_3 = 1.3333333333333333
private const val ASPECT_RATIO_16_9 = 1.7777777777777777
private const val DEFAULT_ASPECT_DELTA = 0.08

private const val ULTRA_WIDE_PREFERRED_PIXELS = 1920 * 1440
private const val ULTRA_WIDE_MIN_PIXELS = 1440 * 1080
private const val ULTRA_WIDE_MAX_PIXELS = 2304 * 1728

private const val WIDE_PREFERRED_PIXELS = 2560 * 1440
private const val WIDE_MIN_PIXELS = 1920 * 1080
private const val WIDE_MAX_PIXELS = 3072 * 1728

private val ULTRA_WIDE_FORMAT_PROFILE = CameraFormatProfile(
    targetAspectRatio = ASPECT_RATIO_4_3,
    preferredPixels = ULTRA_WIDE_PREFERRED_PIXELS,
    minimumPixels = ULTRA_WIDE_MIN_PIXELS,
    maximumPixels = ULTRA_WIDE_MAX_PIXELS,
    maximumAspectDelta = DEFAULT_ASPECT_DELTA
)

private val WIDE_FORMAT_PROFILE = CameraFormatProfile(
    targetAspectRatio = ASPECT_RATIO_16_9,
    preferredPixels = WIDE_PREFERRED_PIXELS,
    minimumPixels = WIDE_MIN_PIXELS,
    maximumPixels = WIDE_MAX_PIXELS,
    maximumAspectDelta = DEFAULT_ASPECT_DELTA
)

private const val REFOCUS_RESTORE_DELAY_MS = 450L
private const val SETTLED_REFOCUS_DELAY_MS = 700L

private val BACK_CAMERA_DEVICE_TYPES = listOf(
    AVCaptureDeviceTypeBuiltInDualWideCamera,
    AVCaptureDeviceTypeBuiltInTripleCamera,
    AVCaptureDeviceTypeBuiltInWideAngleCamera,
    AVCaptureDeviceTypeBuiltInUltraWideCamera
)

private fun AVCaptureDevice.profileName(suffix: String): String = when (deviceType) {
    AVCaptureDeviceTypeBuiltInDualWideCamera -> "dual-wide-$suffix"
    AVCaptureDeviceTypeBuiltInTripleCamera -> "triple-$suffix"
    else -> suffix
}
