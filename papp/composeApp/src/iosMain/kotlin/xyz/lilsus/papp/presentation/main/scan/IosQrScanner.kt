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

    private val session = AVCaptureMultiCamSession()
    private val sessionQueue: dispatch_queue_t = dispatch_queue_create(
        "xyz.lilsus.papp.qr.session",
        null
    )
    private var onQrCodeScanned: ((String) -> Unit)? = null
    private var started = false
    private var paused: Boolean = true
    private var configured = false
    private var previewLayer: AVCaptureVideoPreviewLayer? = null
    private var wideDevice: AVCaptureDevice? = null
    private var ultraWideDevice: AVCaptureDevice? = null
    private var zoomDevice: AVCaptureDevice? = null
    private var zoomBaseFactor: Double = 1.0
    private var captureConfigurationSummary: String = "QR scanner configuration: mode=unconfigured"
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
            // Dual-camera mode currently emits the first accepted payload across both streams.
            // If field reports show wrong-QR picks when wide and ultra-wide see different codes
            // at nearly the same time, introduce a short-window cross-camera arbiter here.
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
            clearConfiguredDevices()
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
                if (!started || paused || zoomDevice !== device) return@dispatch_async
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
        val devices = linkedSetOf<AVCaptureDevice>()
        wideDevice?.let(devices::add)
        ultraWideDevice?.let(devices::add)
        if (devices.isEmpty()) {
            zoomDevice?.let(devices::add)
        }
        if (devices.isEmpty()) return
        for (device in devices) {
            resetFocusAndExposure(device)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun resetFocusAndExposure(device: AVCaptureDevice) {
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

    private fun clearConfiguredDevices() {
        wideDevice = null
        ultraWideDevice = null
        zoomDevice = null
        zoomBaseFactor = 1.0
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

    @OptIn(ExperimentalForeignApi::class)
    private fun configureSessionIfNeeded() {
        if (configured) return

        session.beginConfiguration()
        var success = false
        var fallbackReason: String? = null
        try {
            removeAllInputsAndOutputs()
            removeSubjectAreaObserver()
            clearConfiguredDevices()

            val discovered = discoverBackCameras()
            val wide = discovered.wide
            val ultraWide = discovered.ultraWide

            if (
                wide != null &&
                ultraWide != null &&
                wide !== ultraWide &&
                discovered.canUseWideUltraPair
            ) {
                success = configureMultiCameraSession(
                    wide = wide,
                    ultraWide = ultraWide
                )
                if (!success) {
                    fallbackReason = FALLBACK_REASON_MULTI_CAM_PAIRING_FAILED
                }
            } else {
                fallbackReason = when {
                    wide == null && ultraWide == null -> "no back camera available"
                    ultraWide == null -> "ultra-wide camera unavailable"
                    wide == null -> "wide camera unavailable"
                    !discovered.multiCamSessionSupported -> FALLBACK_REASON_MULTI_CAM_UNSUPPORTED
                    !discovered.pairInSupportedSet -> FALLBACK_REASON_MULTI_CAM_PAIR_NOT_SUPPORTED
                    else -> "wide and ultra-wide resolved to the same device"
                }
                NSLog("Skipping dual-camera configuration: $fallbackReason")
            }

            if (!success) {
                removeAllInputsAndOutputs()
                clearConfiguredDevices()
                val fallbackDevice = when {
                    wide != null &&
                        ultraWide != null &&
                        fallbackReason in DUAL_CAMERA_FALLBACK_REASONS -> ultraWide

                    else ->
                        wide
                            ?: ultraWide
                }
                    ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
                success = configureSingleCameraSession(
                    device = fallbackDevice,
                    preferredWide = wide,
                    preferredUltraWide = ultraWide,
                    fallbackReason = fallbackReason
                )
            }
        } finally {
            session.commitConfiguration()
        }

        configured = success
        if (!success) {
            clearConfiguredDevices()
            lastAppliedZoomFactor = null
            captureConfigurationSummary = "QR scanner configuration failed"
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
        val wide = devices.firstOrNull {
            it.deviceType == AVCaptureDeviceTypeBuiltInWideAngleCamera
        }
        val ultraWide = devices.firstOrNull {
            it.deviceType == AVCaptureDeviceTypeBuiltInUltraWideCamera
        }
        val pairInSupportedSet = if (wide != null && ultraWide != null && wide !== ultraWide) {
            supportsMultiCamPair(
                discoverySession = discoverySession,
                wide = wide,
                ultraWide = ultraWide
            )
        } else {
            false
        }

        val result = BackCameraDiscoveryResult(
            wide = wide,
            ultraWide = ultraWide,
            multiCamSessionSupported = AVCaptureMultiCamSession.isMultiCamSupported(),
            pairInSupportedSet = pairInSupportedSet
        )
        NSLog(
            "Back camera discovery: wide=${wide?.localizedName ?: "none"}, " +
                "ultraWide=${ultraWide?.localizedName ?: "none"}, " +
                "multiCamSupported=${result.multiCamSessionSupported}, " +
                "wideUltraPairSupported=${result.pairInSupportedSet}"
        )
        return result
    }

    private fun supportsMultiCamPair(
        discoverySession: AVCaptureDeviceDiscoverySession,
        wide: AVCaptureDevice,
        ultraWide: AVCaptureDevice
    ): Boolean {
        @Suppress("UNCHECKED_CAST")
        val supportedSets =
            discoverySession.supportedMultiCamDeviceSets as? List<Set<AVCaptureDevice>>
                ?: return false
        if (supportedSets.isEmpty()) return false

        val wideId = wide.uniqueID
        val ultraWideId = ultraWide.uniqueID
        return supportedSets.any { supportedSet ->
            val ids = supportedSet.map { it.uniqueID }.toSet()
            ids.contains(wideId) && ids.contains(ultraWideId)
        }
    }

    private fun configureSingleCameraSession(
        device: AVCaptureDevice?,
        preferredWide: AVCaptureDevice?,
        preferredUltraWide: AVCaptureDevice?,
        fallbackReason: String?
    ): Boolean {
        if (device == null) {
            session.sessionPreset = AVCaptureSessionPresetHigh
            NSLog("Failed to acquire capture device for QR scanning")
            return false
        }

        val (profileName, profile) = when {
            preferredUltraWide != null && device === preferredUltraWide -> {
                "ultra-wide-4:3-single" to ULTRA_WIDE_SINGLE_FORMAT_PROFILE
            }

            else -> {
                "wide" to WIDE_FORMAT_PROFILE
            }
        }
        val selectedFormat = selectAndApplyFormat(
            device = device,
            profile = profile,
            requireMultiCamSupport = false
        )
        session.sessionPreset = if (selectedFormat != null) {
            AVCaptureSessionPresetInputPriority
        } else {
            AVCaptureSessionPresetHigh
        }

        val input = createDeviceInput(device)
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
        configureMetadataOutput(metadataOutput)

        wideDevice = preferredWide?.takeIf { it === device }
        ultraWideDevice = preferredUltraWide?.takeIf { it === device }
        zoomDevice = wideDevice ?: device
        // In single-camera mode wide takes over full scanning duty, so we start at 1x.
        zoomBaseFactor = DEFAULT_BASE_ZOOM_FACTOR
        zoomDevice?.let(::installSubjectAreaObserver)
        captureConfigurationSummary = buildSingleCameraSummary(
            device = device,
            profileName = profileName,
            selectedFormat = selectedFormat,
            fallbackReason = fallbackReason
        )
        return true
    }

    private fun configureMultiCameraSession(
        wide: AVCaptureDevice,
        ultraWide: AVCaptureDevice
    ): Boolean {
        session.sessionPreset = AVCaptureSessionPresetInputPriority

        val ultraWideRawCandidates = collectFormatCandidates(ultraWide)
            .filter { candidate ->
                candidate.isMultiCamSupported &&
                    candidate.maxFrameRate >= MIN_CAPTURE_FPS
            }
        val wideRawCandidates = collectFormatCandidates(wide)
            .filter { candidate ->
                candidate.isMultiCamSupported &&
                    candidate.maxFrameRate >= MIN_CAPTURE_FPS
            }
        val ultraWideCandidates = rankFormatCandidates(
            candidates = ultraWideRawCandidates,
            profile = ULTRA_WIDE_MULTI_CAM_FORMAT_PROFILE,
            minimumPixelHint = 0
        ).take(MAX_FORMAT_CANDIDATES_PER_CAMERA)
        val wideCandidates = rankFormatCandidates(
            candidates = wideRawCandidates,
            profile = WIDE_FORMAT_PROFILE,
            minimumPixelHint = 0
        ).take(MAX_FORMAT_CANDIDATES_PER_CAMERA)
        val ultraWideRawCount = ultraWideRawCandidates.size
        val wideRawCount = wideRawCandidates.size
        NSLog(
            "Multi-cam candidates (fps>=$MIN_CAPTURE_FPS): " +
                "wideSelected=${wideCandidates.size}/$wideRawCount, " +
                "ultraWideSelected=${ultraWideCandidates.size}/$ultraWideRawCount"
        )
        if (ultraWideCandidates.isEmpty() || wideCandidates.isEmpty()) {
            NSLog("No multi-cam compatible formats available for dual camera QR scanning")
            return false
        }

        var attemptedPairs = 0
        var graphRejectedPairs = 0
        for (ultraWideCandidate in ultraWideCandidates) {
            if (!applyFormatCandidate(ultraWide, ultraWideCandidate)) continue

            val prioritizedWide = rankFormatCandidates(
                candidates = wideCandidates,
                profile = WIDE_FORMAT_PROFILE,
                minimumPixelHint = ultraWideCandidate.pixelCount + 1
            )
            val fallbackWide = rankFormatCandidates(
                candidates = wideCandidates,
                profile = WIDE_FORMAT_PROFILE,
                minimumPixelHint = 0
            )
            val wideCandidatesForUltra = linkedSetOf<CameraFormatCandidate>().apply {
                addAll(prioritizedWide)
                addAll(fallbackWide)
            }

            for (wideCandidate in wideCandidatesForUltra) {
                attemptedPairs += 1
                if (!applyFormatCandidate(wide, wideCandidate)) continue
                if (!configureMultiCameraGraph(wide, ultraWide)) {
                    graphRejectedPairs += 1
                    removeAllInputsAndOutputs()
                    continue
                }

                wideDevice = wide
                ultraWideDevice = ultraWide
                zoomDevice = wide
                zoomBaseFactor = WIDE_MULTI_CAM_BASE_ZOOM_FACTOR
                installSubjectAreaObserver(wide)
                captureConfigurationSummary = buildMultiCameraSummary(
                    wide = wide,
                    ultraWide = ultraWide,
                    wideFormat = wideCandidate,
                    ultraWideFormat = ultraWideCandidate
                )
                return true
            }
        }

        NSLog(
            "Failed to establish a compatible wide + ultra-wide pair " +
                "(wideCandidates=${wideCandidates.size}, " +
                "ultraWideCandidates=${ultraWideCandidates.size}, " +
                "attemptedPairs=$attemptedPairs, graphRejectedPairs=$graphRejectedPairs)"
        )
        return false
    }

    private fun configureMultiCameraGraph(
        wide: AVCaptureDevice,
        ultraWide: AVCaptureDevice
    ): Boolean {
        val wideInput = createDeviceInput(wide) ?: return false
        val ultraWideInput = createDeviceInput(ultraWide) ?: return false
        if (!session.canAddInput(wideInput) || !session.canAddInput(ultraWideInput)) {
            NSLog(
                "Multi-cam graph rejected inputs: canAddWide=${session.canAddInput(wideInput)}, " +
                    "canAddUltraWide=${session.canAddInput(ultraWideInput)}"
            )
            return false
        }

        // Add wide first so optional preview binds to wide as the first eligible video input.
        session.addInputWithNoConnections(wideInput)
        session.addInputWithNoConnections(ultraWideInput)

        val wideOutput = AVCaptureMetadataOutput()
        val ultraWideOutput = AVCaptureMetadataOutput()
        if (!session.canAddOutput(wideOutput) || !session.canAddOutput(ultraWideOutput)) {
            NSLog(
                "Multi-cam graph rejected metadata outputs: " +
                    "canAddWideOutput=${session.canAddOutput(wideOutput)}, " +
                    "canAddUltraWideOutput=${session.canAddOutput(ultraWideOutput)}"
            )
            return false
        }
        session.addOutputWithNoConnections(wideOutput)
        session.addOutputWithNoConnections(ultraWideOutput)

        val widePort = firstMetadataObjectPort(wideInput)
        val ultraWidePort = firstMetadataObjectPort(ultraWideInput)
        if (widePort == null || ultraWidePort == null) {
            NSLog(
                "Multi-cam graph missing metadata-object ports: " +
                    "widePort=$widePort, ultraWidePort=$ultraWidePort, " +
                    "widePorts=${portsSummary(wideInput)}, " +
                    "ultraWidePorts=${portsSummary(ultraWideInput)}"
            )
            return false
        }

        val wideConnection = AVCaptureConnection.connectionWithInputPorts(
            ports = listOf(widePort),
            output = wideOutput
        )
        if (!session.canAddConnection(wideConnection)) {
            NSLog("Multi-cam graph rejected wide metadata connection")
            return false
        }
        session.addConnection(wideConnection)

        val ultraWideConnection = AVCaptureConnection.connectionWithInputPorts(
            ports = listOf(ultraWidePort),
            output = ultraWideOutput
        )
        if (!session.canAddConnection(ultraWideConnection)) {
            NSLog("Multi-cam graph rejected ultra-wide metadata connection")
            return false
        }
        session.addConnection(ultraWideConnection)

        configureMetadataOutput(wideOutput)
        configureMetadataOutput(ultraWideOutput)
        return true
    }

    private fun configureMetadataOutput(metadataOutput: AVCaptureMetadataOutput) {
        metadataOutput.setMetadataObjectsDelegate(metadataDelegate, sessionQueue)
        metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
    }

    private fun firstMetadataObjectPort(input: AVCaptureDeviceInput): AVCaptureInputPort? {
        @Suppress("UNCHECKED_CAST")
        val ports = input.ports as? List<AVCaptureInputPort> ?: return null
        return ports.firstOrNull { it.mediaType == AVMediaTypeMetadataObject }
    }

    private fun portsSummary(input: AVCaptureDeviceInput): String {
        @Suppress("UNCHECKED_CAST")
        val ports = input.ports as? List<AVCaptureInputPort> ?: return "none"
        if (ports.isEmpty()) return "none"
        return ports.joinToString(separator = ",") { it.mediaType ?: "unknown" }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun selectAndApplyFormat(
        device: AVCaptureDevice,
        profile: CameraFormatProfile,
        requireMultiCamSupport: Boolean,
        minimumPixelHint: Int = 0
    ): CameraFormatCandidate? {
        val candidates = collectFormatCandidates(device)
            .filter { candidate ->
                if (requireMultiCamSupport && !candidate.isMultiCamSupported) return@filter false
                candidate.maxFrameRate >= MIN_CAPTURE_FPS
            }
        if (candidates.isEmpty()) {
            return null
        }

        val ranked = rankFormatCandidates(
            candidates = candidates,
            profile = profile,
            minimumPixelHint = minimumPixelHint
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
                    maxFrameRate = maxFrameRate,
                    isMultiCamSupported = format.isMultiCamSupported()
                )
            )
        }
        return candidates
    }

    private fun rankFormatCandidates(
        candidates: List<CameraFormatCandidate>,
        profile: CameraFormatProfile,
        minimumPixelHint: Int
    ): List<CameraFormatCandidate> {
        val minimumPixels = maxOf(profile.minimumPixels, minimumPixelHint)
        val tiers = listOf(
            FormatSelectionTier(
                minimumPixels = minimumPixels,
                enforceAspect = true,
                enforceMaximumPixels = true
            ),
            FormatSelectionTier(
                minimumPixels = minimumPixels,
                enforceAspect = true,
                enforceMaximumPixels = false
            ),
            FormatSelectionTier(
                minimumPixels = profile.minimumPixels,
                enforceAspect = true,
                enforceMaximumPixels = false
            ),
            FormatSelectionTier(
                minimumPixels = profile.minimumPixels,
                enforceAspect = false,
                enforceMaximumPixels = false
            ),
            FormatSelectionTier(
                minimumPixels = 0,
                enforceAspect = false,
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
            return@memScoped true
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
    private fun applyZoomIfNeeded(previousRequestedZoom: Float = desiredZoomFraction) {
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
            if (zoomDevice !== device) return@dispatch_after
            resetFocusAndExposure()
        }
    }

    private fun buildSingleCameraSummary(
        device: AVCaptureDevice,
        profileName: String,
        selectedFormat: CameraFormatCandidate?,
        fallbackReason: String?
    ): String {
        val formatSummary = selectedFormat?.let(::formatSummary) ?: "system-default (preset=high)"
        val fallbackSuffix = fallbackReason?.let { "; fallback=$it" } ?: ""
        return "QR scanner configuration: mode=single; profile=$profileName; " +
            "device=${deviceSummary(device)}; format=$formatSummary; " +
            "targetFps=$TARGET_CAPTURE_FPS; baseZoom=$zoomBaseFactor$fallbackSuffix"
    }

    private fun buildMultiCameraSummary(
        wide: AVCaptureDevice,
        ultraWide: AVCaptureDevice,
        wideFormat: CameraFormatCandidate,
        ultraWideFormat: CameraFormatCandidate
    ): String {
        val wideDeviceSummary = deviceSummary(wide)
        val wideFormatSummary = formatSummary(wideFormat)
        val ultraWideDeviceSummary = deviceSummary(ultraWide)
        val ultraWideFormatSummary = formatSummary(ultraWideFormat)
        val wideSummary = "wide=$wideDeviceSummary; wideFormat=$wideFormatSummary"
        val ultraWideSummary =
            "ultraWide=$ultraWideDeviceSummary; " +
                "ultraWideFormat=$ultraWideFormatSummary"
        val zoomSummary = "targetFps=$TARGET_CAPTURE_FPS; zoomDevice=wide; baseZoom=$zoomBaseFactor"
        val costSummary =
            "hardwareCost=${session.hardwareCost}; " +
                "systemPressureCost=${session.systemPressureCost}"
        return "QR scanner configuration: mode=multi; ultraWideProfile=16:9-multicam; " +
            "$wideSummary; $ultraWideSummary; $zoomSummary; $costSummary"
    }

    private fun deviceSummary(device: AVCaptureDevice): String =
        "${device.localizedName}(${device.uniqueID})"

    private fun formatSummary(candidate: CameraFormatCandidate): String =
        "${candidate.width}x${candidate.height}(maxFps=${candidate.maxFrameRate},multiCam=${candidate.isMultiCamSupported})"
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
    val maxFrameRate: Double,
    val isMultiCamSupported: Boolean
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
    val wide: AVCaptureDevice?,
    val ultraWide: AVCaptureDevice?,
    val multiCamSessionSupported: Boolean,
    val pairInSupportedSet: Boolean
) {
    val canUseWideUltraPair: Boolean
        get() = multiCamSessionSupported && pairInSupportedSet
}

private const val ZOOM_FACTOR_EPSILON = 0.01
private const val ZOOM_PULSE_FROM_THRESHOLD = 0.12f
private const val ZOOM_DEFAULT_THRESHOLD = 0.02f
private const val FALLBACK_REASON_MULTI_CAM_UNSUPPORTED = "platform multi-cam unsupported"
private const val FALLBACK_REASON_MULTI_CAM_PAIR_NOT_SUPPORTED =
    "wide+ultra-wide pair not listed in supportedMultiCamDeviceSets"
private const val FALLBACK_REASON_MULTI_CAM_PAIRING_FAILED = "multi-cam format pairing failed"
private const val ZOOM_PULSE_DELTA_THRESHOLD = 0.2f

private const val DEFAULT_BASE_ZOOM_FACTOR = 1.0
private const val WIDE_MULTI_CAM_BASE_ZOOM_FACTOR = 2.0

private const val TARGET_CAPTURE_FPS = 30.0
private const val MIN_CAPTURE_FPS = 30.0
private const val MAX_FORMAT_CANDIDATES_PER_CAMERA = 18

private const val ASPECT_RATIO_4_3 = 1.3333333333333333
private const val ASPECT_RATIO_16_9 = 1.7777777777777777
private const val DEFAULT_ASPECT_DELTA = 0.08

private const val ULTRA_WIDE_PREFERRED_PIXELS = 1920 * 1440
private const val ULTRA_WIDE_MIN_PIXELS = 1440 * 1080
private const val ULTRA_WIDE_MAX_PIXELS = 2304 * 1728

private const val ULTRA_WIDE_MULTI_CAM_PREFERRED_PIXELS = 1920 * 1080
private const val ULTRA_WIDE_MULTI_CAM_MIN_PIXELS = 1600 * 900
private const val ULTRA_WIDE_MULTI_CAM_MAX_PIXELS = 2560 * 1440

private const val WIDE_PREFERRED_PIXELS = 2560 * 1440
private const val WIDE_MIN_PIXELS = 1920 * 1080
private const val WIDE_MAX_PIXELS = 3072 * 1728

private val ULTRA_WIDE_SINGLE_FORMAT_PROFILE = CameraFormatProfile(
    targetAspectRatio = ASPECT_RATIO_4_3,
    preferredPixels = ULTRA_WIDE_PREFERRED_PIXELS,
    minimumPixels = ULTRA_WIDE_MIN_PIXELS,
    maximumPixels = ULTRA_WIDE_MAX_PIXELS,
    maximumAspectDelta = DEFAULT_ASPECT_DELTA
)

private val ULTRA_WIDE_MULTI_CAM_FORMAT_PROFILE = CameraFormatProfile(
    targetAspectRatio = ASPECT_RATIO_16_9,
    preferredPixels = ULTRA_WIDE_MULTI_CAM_PREFERRED_PIXELS,
    minimumPixels = ULTRA_WIDE_MULTI_CAM_MIN_PIXELS,
    maximumPixels = ULTRA_WIDE_MULTI_CAM_MAX_PIXELS,
    maximumAspectDelta = DEFAULT_ASPECT_DELTA
)

private val WIDE_FORMAT_PROFILE = CameraFormatProfile(
    targetAspectRatio = ASPECT_RATIO_16_9,
    preferredPixels = WIDE_PREFERRED_PIXELS,
    minimumPixels = WIDE_MIN_PIXELS,
    maximumPixels = WIDE_MAX_PIXELS,
    maximumAspectDelta = DEFAULT_ASPECT_DELTA
)

private const val REFOCUS_RESTORE_DELAY_MS = 220L

private val BACK_CAMERA_DEVICE_TYPES = listOf(
    AVCaptureDeviceTypeBuiltInWideAngleCamera,
    AVCaptureDeviceTypeBuiltInUltraWideCamera
)

private val DUAL_CAMERA_FALLBACK_REASONS = setOf(
    FALLBACK_REASON_MULTI_CAM_UNSUPPORTED,
    FALLBACK_REASON_MULTI_CAM_PAIR_NOT_SUPPORTED,
    FALLBACK_REASON_MULTI_CAM_PAIRING_FAILED
)
