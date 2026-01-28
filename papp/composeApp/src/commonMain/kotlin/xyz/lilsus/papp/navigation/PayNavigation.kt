package xyz.lilsus.papp.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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

/** Fraction of screen height needed to drag for full zoom range. */
private const val ZOOM_DRAG_RANGE = 0.4f

/** Horizontal swipe threshold in pixels to trigger wallet switch. */
private const val SWIPE_THRESHOLD = 100f

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
    val scope = rememberCoroutineScope()

    var hasRequestedPermission by remember { mutableStateOf(false) }
    var scannerStarted by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // Use Animatable for smooth zoom with snap-back animation
    val zoomFraction = remember { Animatable(0f) }

    // Track drag start position for absolute distance calculation
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }

    fun hidePreview() {
        if (previewVisible) {
            previewVisible = false
            scope.launch {
                zoomFraction.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            scannerController.setZoom(0f)
        }
    }

    // Sync zoom changes to camera controller
    LaunchedEffect(Unit) {
        snapshotFlow { zoomFraction.value }
            .collect { zoom -> scannerController.setZoom(zoom) }
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
        }
    }

    fun startScannerIfNeeded() {
        if (scannerStarted) return
        if (!cameraPermission.hasPermission) return
        scannerController.start { rawValue ->
            viewModel.dispatch(MainIntent.QrCodeScanned(rawValue))
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
    val wallets by viewModel.wallets.collectAsState()
    LaunchedEffect(uiState) {
        if (uiState != MainUiState.Active) {
            hidePreview()
        }
    }

    // Track horizontal swipe for wallet switching
    var swipeStartX by remember { mutableStateOf(0f) }

    val canSwipeWallets = uiState == MainUiState.Active && wallets.size > 1

    val gestureModifier = Modifier
        .fillMaxSize()
        .onSizeChanged { containerSize = it }
        .pointerInput(canSwipeWallets) {
            if (!canSwipeWallets) return@pointerInput
            detectHorizontalDragGestures(
                onDragStart = { offset -> swipeStartX = offset.x },
                onDragEnd = { },
                onHorizontalDrag = { change, _ ->
                    val totalDrag = change.position.x - swipeStartX
                    if (totalDrag > SWIPE_THRESHOLD) {
                        viewModel.dispatch(MainIntent.SwipeWalletPrevious)
                        swipeStartX = change.position.x
                    } else if (totalDrag < -SWIPE_THRESHOLD) {
                        viewModel.dispatch(MainIntent.SwipeWalletNext)
                        swipeStartX = change.position.x
                    }
                }
            )
        }
        .pointerInput(cameraPermission.hasPermission) {
            if (!cameraPermission.hasPermission) return@pointerInput
            detectDragGesturesAfterLongPress(
                onDragStart = { startPosition ->
                    startScannerIfNeeded()
                    previewVisible = true
                    dragStartPosition = startPosition
                    scope.launch { zoomFraction.snapTo(0f) }
                    scannerController.resume()
                },
                onDragCancel = {
                    hidePreview()
                },
                onDragEnd = {
                    hidePreview()
                },
                onDrag = { change, _ ->
                    val height = containerSize.height.toFloat()
                        .takeIf { it > 0f }
                        ?: return@detectDragGesturesAfterLongPress

                    // Calculate drag distance from start position (absolute, not accumulated)
                    val dragDistance = change.position.y - dragStartPosition.y

                    // Linear mapping: drag 40% of screen height = full zoom range
                    // The logarithmic zoom scaling in the camera controller handles
                    // perceptual uniformity, so we keep the gesture linear.
                    val newZoom = (dragDistance / (height * ZOOM_DRAG_RANGE))
                        .coerceIn(0f, 1f)

                    scope.launch { zoomFraction.snapTo(newZoom) }
                }
            )
        }

    Box(modifier = gestureModifier) {
        MainScreen(
            onNavigateSettings = onNavigateToSettings,
            onNavigateConnectWallet = onNavigateToConnectWallet,
            uiState = uiState,
            wallets = wallets,
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
            onPendingTap = { id -> viewModel.dispatch(MainIntent.TapPending(id)) },
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
