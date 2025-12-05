package xyz.lilsus.papp.navigation

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlin.math.absoluteValue
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import xyz.lilsus.papp.navigation.DonationNavigation.consume
import xyz.lilsus.papp.navigation.DonationNavigation.events
import xyz.lilsus.papp.presentation.main.MainEvent
import xyz.lilsus.papp.presentation.main.MainIntent
import xyz.lilsus.papp.presentation.main.MainScreen
import xyz.lilsus.papp.presentation.main.MainUiState
import xyz.lilsus.papp.presentation.main.rememberMainViewModel
import xyz.lilsus.papp.presentation.main.scan.CameraPreviewHost
import xyz.lilsus.papp.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.papp.presentation.main.scan.rememberQrScannerController

@Serializable
object Pay

private const val PREVIEW_ZOOM_DRAG_FRACTION = 0.33f
private const val PREVIEW_ZOOM_BASE_SPEED = 1.1f
private const val PREVIEW_ZOOM_ACCELERATION = 1.6f

fun NavGraphBuilder.paymentScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToConnectWallet: (String) -> Unit = {}
) {
    composable<Pay> {
        MainScreenEntry(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToConnectWallet = onNavigateToConnectWallet
        )
    }
}

@Composable
private fun MainScreenEntry(
    onNavigateToSettings: () -> Unit,
    onNavigateToConnectWallet: (String) -> Unit
) {
    val viewModel = rememberMainViewModel()
    val cameraPermission = rememberCameraPermissionState()
    val scannerController = rememberQrScannerController()
    var hasRequestedPermission by remember { mutableStateOf(false) }
    var scannerStarted by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var zoomFraction by remember { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var dragStartZoom by remember { mutableFloatStateOf(0f) }

    fun hidePreview() {
        if (previewVisible) {
            previewVisible = false
            zoomFraction = 0f
            scannerController.setZoom(0f)
        }
    }

    DisposableEffect(viewModel, scannerController) {
        onDispose {
            scannerController.stop()
            scannerStarted = false
            previewVisible = false
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MainEvent.ShowError -> Unit // TODO: hook up snackbar/toast presentation.
            }
        }
    }

    LaunchedEffect(viewModel) {
        events.collectLatest { request ->
            viewModel.dispatch(
                MainIntent.StartDonation(
                    amountSats = request.amountSats,
                    address = request.address
                )
            )
            consume()
        }
    }

    fun startScannerIfNeeded() {
        if (scannerStarted) return
        if (!cameraPermission.hasPermission) return
        scannerController.start { invoice ->
            viewModel.dispatch(MainIntent.InvoiceDetected(invoice))
        }
        scannerStarted = true
    }

    LaunchedEffect(cameraPermission.hasPermission) {
        if (cameraPermission.hasPermission) {
            hasRequestedPermission = false
        } else {
            if (scannerStarted) {
                scannerController.stop()
                scannerStarted = false
            }
            hidePreview()
            if (!hasRequestedPermission) {
                hasRequestedPermission = true
                cameraPermission.request()
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val pendingPayments by viewModel.pendingPayments.collectAsState()
    LaunchedEffect(uiState) {
        if (uiState != MainUiState.Active) {
            hidePreview()
        }
    }

    val gestureModifier = Modifier
        .fillMaxSize()
        .onSizeChanged { containerSize = it }
        .pointerInput(cameraPermission.hasPermission) {
            if (!cameraPermission.hasPermission) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    startScannerIfNeeded()
                    previewVisible = true
                    accumulatedDrag = 0f
                    dragStartZoom = zoomFraction
                    scannerController.resume()
                    scannerController.setZoom(zoomFraction)
                },
                onDragCancel = {
                    hidePreview()
                },
                onDragEnd = {
                    hidePreview()
                },
                onDrag = { _, dragAmount ->
                    val height =
                        containerSize.height.takeIf { it > 0 }
                            ?: return@detectDragGesturesAfterLongPress
                    accumulatedDrag = (accumulatedDrag + dragAmount.y)
                    val normalized = (
                        accumulatedDrag / (height * PREVIEW_ZOOM_DRAG_FRACTION)
                        ).coerceIn(-1f, 1f)
                    val curve = normalized * (
                        PREVIEW_ZOOM_BASE_SPEED +
                            PREVIEW_ZOOM_ACCELERATION * normalized.absoluteValue
                        )
                    zoomFraction = (dragStartZoom + curve).coerceIn(0f, 1f)
                    scannerController.setZoom(zoomFraction)
                }
            )
        }

    Box(modifier = gestureModifier) {
        MainScreen(
            onNavigateSettings = onNavigateToSettings,
            onNavigateConnectWallet = onNavigateToConnectWallet,
            uiState = uiState,
            pendingPayments = pendingPayments,
            onManualAmountKeyPress = { key ->
                viewModel.dispatch(MainIntent.ManualAmountKeyPress(key))
            },
            onManualAmountPreset = { amount ->
                viewModel.dispatch(MainIntent.ManualAmountPreset(amount))
            },
            onManualAmountSubmit = { viewModel.dispatch(MainIntent.ManualAmountSubmit) },
            onManualAmountDismiss = { viewModel.dispatch(MainIntent.ManualAmountDismiss) },
            onConfirmPaymentSubmit = { viewModel.dispatch(MainIntent.ConfirmPaymentSubmit) },
            onConfirmPaymentDismiss = { viewModel.dispatch(MainIntent.ConfirmPaymentDismiss) },
            onResultDismiss = { viewModel.dispatch(MainIntent.DismissResult) },
            onPendingNoticeDismiss = { id ->
                viewModel.dispatch(MainIntent.DismissPendingNotice(id))
            },
            onPendingItemClick = { id ->
                viewModel.dispatch(MainIntent.SelectPendingItem(id))
            },
            onRequestScannerStart = {
                if (!cameraPermission.hasPermission) {
                    if (!hasRequestedPermission) {
                        hasRequestedPermission = true
                        cameraPermission.request()
                    }
                } else {
                    startScannerIfNeeded()
                }
            },
            onScannerResume = {
                if (!cameraPermission.hasPermission) return@MainScreen
                startScannerIfNeeded()
                scannerController.resume()
            },
            onScannerPause = {
                if (scannerStarted) {
                    scannerController.pause()
                }
            },
            isCameraPermissionGranted = cameraPermission.hasPermission,
            modifier = if (previewVisible) Modifier.alpha(0.05f) else Modifier
        )

        if (previewVisible) {
            CameraPreviewHost(
                controller = scannerController,
                visible = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
