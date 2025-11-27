package xyz.lilsus.papp.presentation.main.scan

import android.Manifest
import android.content.Context
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScanner"

actual class CameraPreviewSurface internal constructor(val previewView: PreviewView)

@Composable
actual fun rememberQrScannerController(): QrScannerController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    return remember(context.applicationContext, lifecycleOwner) {
        AndroidQrScannerController(
            context = context.applicationContext,
            lifecycleOwner = lifecycleOwner
        )
    }
}

@Composable
actual fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val permissionGranted = remember {
        mutableStateOf(isCameraPermissionGranted(context))
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted.value = granted
    }

    LaunchedEffect(context) {
        permissionGranted.value = isCameraPermissionGranted(context)
    }

    return remember(launcher) {
        AndroidCameraPermissionState(
            permissionState = permissionGranted,
            launcher = launcher
        )
    }
}

@Composable
actual fun CameraPreviewHost(
    controller: QrScannerController,
    visible: Boolean,
    modifier: Modifier
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val surface = remember { CameraPreviewSurface(previewView) }

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
        AndroidView(
            modifier = modifier,
            factory = { previewView }
        )
    }
}

private class AndroidCameraPermissionState(
    private val permissionState: MutableState<Boolean>,
    private val launcher: ActivityResultLauncher<String>
) : CameraPermissionState {
    override val hasPermission: Boolean
        get() = permissionState.value

    override fun request() {
        if (!permissionState.value) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
}

private class AndroidQrScannerController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : QrScannerController {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analyzer: QrCodeAnalyzer? = null
    private var previewUseCase: Preview? = null
    private var previewSurface: CameraPreviewSurface? = null
    private var analysisExecutor: ExecutorService? = null
    private var onQrCodeScanned: ((String) -> Unit)? = null
    private val isActive = AtomicBoolean(false)
    private val isBound = AtomicBoolean(false)
    private var camera: Camera? = null

    override fun start(onQrCodeScanned: (String) -> Unit) {
        this.onQrCodeScanned = onQrCodeScanned
        if (isBound.compareAndSet(false, true)) {
            bindCamera()
        } else {
            resume()
        }
    }

    override fun pause() {
        isActive.set(false)
        analyzer?.pause()
    }

    override fun resume() {
        if (!isBound.get()) return
        isActive.set(true)
        analyzer?.resume()
    }

    override fun stop() {
        pause()
        cameraProvider?.unbindAll()
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        analyzer?.close()
        analyzer = null
        previewUseCase = null
        previewSurface = null
        camera = null
        analysisExecutor?.shutdown()
        analysisExecutor = null
        cameraProvider = null
        onQrCodeScanned = null
        isBound.set(false)
    }

    override fun bindPreview(surface: CameraPreviewSurface) {
        previewSurface = surface
        previewUseCase?.surfaceProvider = surface.previewView.surfaceProvider
    }

    override fun unbindPreview() {
        previewUseCase?.surfaceProvider = null
        previewSurface = null
    }

    override fun setZoom(zoomFraction: Float) {
        val target = camera ?: return
        val clamped = zoomFraction.coerceIn(0f, 1f)
        target.cameraControl.setLinearZoom(clamped)
    }

    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                try {
                    if (!isBound.get()) return@addListener
                    val provider = cameraProviderFuture.get()
                    if (!isBound.get()) return@addListener
                    cameraProvider = provider

                    if (!isBound.get()) return@addListener
                    val analysisExecutor =
                        analysisExecutor ?: Executors.newSingleThreadExecutor().also {
                            analysisExecutor = it
                        }
                    val mainExecutor = ContextCompat.getMainExecutor(context)

                    if (!isBound.get()) return@addListener
                    val analyzer = analyzer ?: QrCodeAnalyzer(
                        barcodeScanner = newBarcodeScanner(),
                        active = isActive,
                        mainExecutor = mainExecutor,
                        analysisExecutor = analysisExecutor,
                        onQrCodeScanned = { value ->
                            onQrCodeScanned?.invoke(value)
                        }
                    ).also {
                        analyzer = it
                    }

                    if (!isBound.get()) return@addListener
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    // NOTE: MlKit recommends around 1920x1080 resolution which is 16:9.
                                    // But we do not need a WYSIWYG experience so we prefer the most common
                                    // native sensor aspect ratio which is 4:3.
                                    ResolutionStrategy(
                                        Size(1920, 1440),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER
                                    )
                                )
                                .build()
                        )
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
                        }

                    imageAnalysis = analysis

                    val preview = Preview.Builder()
                        .build()
                        .also { previewUseCase = it }

                    previewSurface?.let { surface ->
                        preview.surfaceProvider = surface.previewView.surfaceProvider
                    }

                    if (!isBound.get()) {
                        provider.unbindAll()
                        return@addListener
                    }

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )

                    resume()
                } catch (failure: Throwable) {
                    Log.e(TAG, "Failed to bind CameraX use cases", failure)
                    stop()
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun newBarcodeScanner(): BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
}

private class QrCodeAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val active: AtomicBoolean,
    private val mainExecutor: Executor,
    private val analysisExecutor: Executor,
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    fun pause() {
        active.set(false)
    }

    fun resume() {
        active.set(true)
    }

    fun close() {
        pause()
        barcodeScanner.close()
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        try {
            if (!active.get()) {
                image.close()
                return
            }
            val mediaImage = image.image
            if (mediaImage == null) {
                image.close()
                return
            }

            val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            barcodeScanner.process(input)
                .addOnSuccessListener(analysisExecutor) { barcodes ->
                    val value = barcodes.firstOrNull()?.rawValue
                    if (value != null) {
                        mainExecutor.execute { onQrCodeScanned(value) }
                    }
                }
                .addOnFailureListener(analysisExecutor) { error ->
                    Log.e(TAG, "Barcode scanning failed", error)
                }
                .addOnCompleteListener(analysisExecutor) {
                    image.close()
                }
        } catch (failure: Throwable) {
            image.close()
            Log.e(TAG, "Unexpected failure while analyzing image", failure)
        }
    }
}

private fun isCameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
