package xyz.lilsus.papp.presentation.main.scan

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
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

private class AndroidCameraPermissionState(
    private val permissionState: MutableState<Boolean>,
    private val launcher: ActivityResultLauncher<String>,
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
    private val lifecycleOwner: LifecycleOwner,
) : QrScannerController {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var analyzer: QrCodeAnalyzer? = null
    private var analysisExecutor: ExecutorService? = null
    private var onQrCodeScanned: ((String) -> Unit)? = null
    private val isActive = AtomicBoolean(false)
    private val isBound = AtomicBoolean(false)

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
        analysisExecutor?.shutdown()
        analysisExecutor = null
        cameraProvider = null
        onQrCodeScanned = null
        isBound.set(false)
    }

    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider

                    val analysisExecutor = analysisExecutor ?: Executors.newSingleThreadExecutor().also {
                        analysisExecutor = it
                    }
                    val mainExecutor = ContextCompat.getMainExecutor(context)

                    val analyzer = analyzer ?: QrCodeAnalyzer(
                        barcodeScanner = newBarcodeScanner(),
                        active = isActive,
                        analysisExecutor = analysisExecutor,
                        mainExecutor = mainExecutor,
                        onQrCodeScanned = { value ->
                            onQrCodeScanned?.invoke(value)
                        }
                    ).also {
                        analyzer = it
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { imageAnalysis ->
                            imageAnalysis.setAnalyzer(analysisExecutor, analyzer)
                        }

                    imageAnalysis = analysis

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
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

    private fun newBarcodeScanner(): BarcodeScanner {
        return BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }
}

private class QrCodeAnalyzer(
    private val barcodeScanner: BarcodeScanner,
    private val active: AtomicBoolean,
    private val analysisExecutor: ExecutorService,
    private val mainExecutor: Executor,
    private val onQrCodeScanned: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val processing = AtomicBoolean(false)

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

            if (!processing.compareAndSet(false, true)) {
                image.close()
                return
            }

            val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
            barcodeScanner.process(input)
                .addOnSuccessListener(analysisExecutor) { barcodes ->
                    val value = barcodes.firstOrNull()?.rawValue
                    if (value != null) {
                        pause()
                        mainExecutor.execute { onQrCodeScanned(value) }
                    }
                }
                .addOnFailureListener(analysisExecutor) { error ->
                    Log.e(TAG, "Barcode scanning failed", error)
                }
                .addOnCompleteListener(analysisExecutor) {
                    processing.set(false)
                    image.close()
                }
        } catch (failure: Throwable) {
            processing.set(false)
            image.close()
            Log.e(TAG, "Unexpected failure while analyzing image", failure)
        }
    }
}

private fun isCameraPermissionGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
