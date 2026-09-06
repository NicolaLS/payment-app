package xyz.lilsus.lasr.feature.walletconnection

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun NwcConnectionQrScannerPreview(
    onQrCodeScanned: (String) -> Unit,
    onCameraPermissionMissing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrCodeScanned = rememberUpdatedState(onQrCodeScanned)
    val currentOnCameraPermissionMissing = rememberUpdatedState(onCameraPermissionMissing)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            isClickable = false
            isFocusable = false
        }
    }
    val scanner = remember(context.applicationContext, lifecycleOwner, previewView) {
        NwcAndroidPreviewScanner(
            context = context.applicationContext,
            lifecycleOwner = lifecycleOwner,
            previewView = previewView
        )
    }

    DisposableEffect(scanner) {
        scanner.start(
            onQrCodeScanned = { currentOnQrCodeScanned.value(it) },
            onCameraPermissionMissing = { currentOnCameraPermissionMissing.value() }
        )
        onDispose(scanner::stop)
    }

    AndroidView(
        modifier = modifier,
        factory = { previewView }
    )
}

private class NwcAndroidPreviewScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var analysis: ImageAnalysis? = null
    private var analyzer: NwcQrAnalyzer? = null
    private var executor: ExecutorService? = null
    private var generation = 0L
    private var running = false

    fun start(onQrCodeScanned: (String) -> Unit, onCameraPermissionMissing: () -> Unit) {
        if (!hasCameraPermission()) {
            onCameraPermissionMissing()
            return
        }
        if (running) return
        running = true
        generation += 1
        val currentGeneration = generation
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                if (!isCurrent(currentGeneration)) return@addListener
                try {
                    val provider = providerFuture.get()
                    if (!isCurrent(currentGeneration)) return@addListener
                    val scannerExecutor = Executors.newSingleThreadExecutor().also {
                        executor = it
                    }
                    val qrAnalyzer = NwcQrAnalyzer { value ->
                        if (isCurrent(currentGeneration)) onQrCodeScanned(value)
                    }.also {
                        analyzer = it
                    }
                    val previewUseCase = Preview.Builder().build().apply {
                        surfaceProvider = previewView.surfaceProvider
                    }.also {
                        preview = it
                    }
                    val analysisUseCase = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1920, 1440),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                                    )
                                )
                                .build()
                        )
                        .build()
                        .apply { setAnalyzer(scannerExecutor, qrAnalyzer) }
                        .also { analysis = it }

                    cameraProvider = provider

                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        previewUseCase,
                        analysisUseCase
                    )
                    camera.cameraInfo.zoomState.value?.minZoomRatio?.let {
                        camera.cameraControl.setZoomRatio(it)
                    }
                } catch (failure: Throwable) {
                    if (!isCurrent(currentGeneration)) return@addListener
                    Log.e(TAG, "Unable to start NWC QR preview", failure)
                    stop()
                    if (!hasCameraPermission() || failure.hasSecurityExceptionCause()) {
                        onCameraPermissionMissing()
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun stop() {
        if (!running && preview == null && analysis == null) return
        generation += 1
        running = false
        val previewUseCase = preview
        val analysisUseCase = analysis
        if (previewUseCase != null && analysisUseCase != null) {
            cameraProvider?.unbind(previewUseCase, analysisUseCase)
        }
        previewUseCase?.surfaceProvider = null
        analysisUseCase?.clearAnalyzer()
        analyzer?.close()
        executor?.shutdown()
        cameraProvider = null
        preview = null
        analysis = null
        analyzer = null
        executor = null
    }

    private fun isCurrent(value: Long): Boolean = running && generation == value

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
}

private class NwcQrAnalyzer(private val onQrCodeScanned: (String) -> Unit) :
    ImageAnalysis.Analyzer {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    private val processing = AtomicBoolean(false)
    private var lastValue: String? = null

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        if (!processing.compareAndSet(false, true)) {
            image.close()
            return
        }
        val mediaImage = image.image
        if (mediaImage == null) {
            processing.set(false)
            image.close()
            return
        }
        scanner.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.trim()?.takeIf(String::isNotEmpty)
                }
                if (value != null && value != lastValue) {
                    lastValue = value
                    onQrCodeScanned(value)
                }
            }
            .addOnFailureListener { failure ->
                Log.d(TAG, "NWC QR frame analysis failed", failure)
            }
            .addOnCompleteListener {
                processing.set(false)
                image.close()
            }
    }

    fun close() {
        scanner.close()
    }
}

private fun Throwable.hasSecurityExceptionCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SecurityException) return true
        current = current.cause
    }
    return false
}

private const val TAG = "NwcQrPreview"
