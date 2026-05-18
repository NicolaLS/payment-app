package xyz.lilsus.papp.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.zIndex
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.toast_bitcoin_address
import lasr.composeapp.generated.resources.toast_bolt12_not_supported
import org.jetbrains.compose.resources.getString
import xyz.lilsus.papp.navigation.DonationNavigation.events
import xyz.lilsus.papp.navigation.PaymentDeepLinkEvents.events as paymentDeepLinkEvents
import xyz.lilsus.papp.navigation.PaymentInputSource
import xyz.lilsus.papp.presentation.common.getErrorMessageFor
import xyz.lilsus.papp.presentation.main.MainEvent
import xyz.lilsus.papp.presentation.main.MainIntent
import xyz.lilsus.papp.presentation.main.MainScreen
import xyz.lilsus.papp.presentation.main.MainUiState
import xyz.lilsus.papp.presentation.main.ToastMessage
import xyz.lilsus.papp.presentation.main.rememberMainViewModel
import xyz.lilsus.papp.presentation.main.scan.CameraPreviewHost
import xyz.lilsus.papp.presentation.main.scan.QrScannerMode
import xyz.lilsus.papp.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.papp.presentation.main.scan.rememberQrScannerController

@Serializable
object Pay

/** Fraction of screen height needed to drag for full zoom range. */
private const val ZOOM_DRAG_RANGE = 0.4f
private const val ZOOM_STEP = 0.01f
private const val PREVIEW_REVEAL_DELAY_MS = 220L
private const val SCANNER_AUTO_RESTART_DELAY_MS = 350L
private const val MAX_SCANNER_AUTO_RESTARTS = 1

/** Horizontal swipe threshold in pixels to trigger wallet switch. */
private const val SWIPE_THRESHOLD = 100f

fun NavGraphBuilder.paymentScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToPaymentSettings: () -> Unit = {},
    onNavigateToContactsSettings: () -> Unit = {},
    onNavigateToConnectWallet: (String) -> Unit = {}
) {
    composable<Pay> {
        MainScreenEntry(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToPaymentSettings = onNavigateToPaymentSettings,
            onNavigateToContactsSettings = onNavigateToContactsSettings,
            onNavigateToConnectWallet = onNavigateToConnectWallet
        )
    }
}

@Composable
private fun MainScreenEntry(
    onNavigateToSettings: () -> Unit,
    onNavigateToPaymentSettings: () -> Unit,
    onNavigateToContactsSettings: () -> Unit,
    onNavigateToConnectWallet: (String) -> Unit
) {
    val viewModel = rememberMainViewModel()
    val cameraPermission = rememberCameraPermissionState()
    val scannerController = rememberQrScannerController()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasRequestedPermission by remember { mutableStateOf(false) }
    var scannerStarted by remember { mutableStateOf(false) }
    var previewPrepared by remember { mutableStateOf(false) }
    var previewRevealRequested by remember { mutableStateOf(false) }
    var previewStreaming by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var scannerMode by remember { mutableStateOf(QrScannerMode.Near) }
    var modeBeforeZoomGesture by remember { mutableStateOf<QrScannerMode?>(null) }
    var previewRevealJob by remember { mutableStateOf<Job?>(null) }
    var scannerRestartRequest by remember { mutableStateOf(0) }
    var scannerAutoRestartAttempts by remember { mutableStateOf(0) }

    // Use Animatable for zoom state synchronization with scanner controller
    val zoomFraction = remember { Animatable(0f) }

    // Track drag start position for absolute distance calculation
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }

    fun hidePreview() {
        previewRevealJob?.cancel()
        previewRevealJob = null
        previewRevealRequested = false
        if (previewPrepared) {
            previewPrepared = false
        }
        // Reset immediately so manual zoom never persists between presses.
        scope.launch { zoomFraction.snapTo(0f) }
        scannerController.setZoom(0f)
    }

    fun updateScannerMode(mode: QrScannerMode) {
        if (scannerMode == mode) return
        scannerMode = mode
        scannerController.setMode(mode)
    }

    fun restoreScannerModeAfterZoomGesture() {
        val originalMode = modeBeforeZoomGesture ?: return
        modeBeforeZoomGesture = null
        updateScannerMode(originalMode)
    }

    // Sync zoom changes to camera controller
    LaunchedEffect(Unit) {
        snapshotFlow { zoomFraction.value }
            .map(::quantizeZoom)
            .distinctUntilChanged()
            .collect { zoom -> scannerController.setZoom(zoom) }
    }

    DisposableEffect(viewModel, scannerController) {
        onDispose {
            previewRevealJob?.cancel()
            scannerController.stop()
            scannerStarted = false
            previewPrepared = false
            previewRevealRequested = false
            previewStreaming = false
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MainEvent.ShowError -> {
                    snackbarHostState.showSnackbar(getErrorMessageFor(event.error))
                }

                // Full-screen errors are shown via UI state
                is MainEvent.ShowToast -> {
                    val message = when (event.message) {
                        ToastMessage.BitcoinAddressNotSupported ->
                            getString(Res.string.toast_bitcoin_address)

                        ToastMessage.Bolt12NotSupported ->
                            getString(Res.string.toast_bolt12_not_supported)
                    }
                    snackbarHostState.showSnackbar(message)
                }

                is MainEvent.NavigateToConnectWallet -> {
                    onNavigateToConnectWallet(event.uri)
                }
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

    LaunchedEffect(viewModel) {
        paymentDeepLinkEvents.collectLatest { request ->
            when (request.source) {
                PaymentInputSource.Camera ->
                    viewModel.dispatch(MainIntent.QrCodeScanned(request.input))

                PaymentInputSource.DeepLink ->
                    viewModel.dispatch(MainIntent.PaymentDeepLinkReceived(request.input))
            }
        }
    }

    fun endZoomGesture() {
        hidePreview()
        restoreScannerModeAfterZoomGesture()
    }

    fun requestCameraPermissionAfterScannerFailure() {
        scannerStarted = false
        previewStreaming = false
        scannerController.stop()
        endZoomGesture()
        hasRequestedPermission = true
        cameraPermission.request()
    }

    fun handleScannerUnavailable() {
        scannerStarted = false
        previewStreaming = false
        endZoomGesture()

        if (scannerAutoRestartAttempts >= MAX_SCANNER_AUTO_RESTARTS) return
        scannerAutoRestartAttempts += 1
        scannerRestartRequest += 1
    }

    fun startScannerIfNeeded(): Boolean {
        if (scannerStarted) return true
        if (!cameraPermission.hasPermission) return false
        scannerController.setMode(scannerMode)
        scannerStarted = scannerController.start(
            onQrCodeScanned = { rawValue ->
                scannerAutoRestartAttempts = 0
                viewModel.dispatch(MainIntent.QrCodeScanned(rawValue))
            },
            onCameraPermissionMissing = ::requestCameraPermissionAfterScannerFailure,
            onScannerUnavailable = ::handleScannerUnavailable
        )
        return scannerStarted
    }

    LaunchedEffect(scannerRestartRequest) {
        if (scannerRestartRequest == 0) return@LaunchedEffect
        delay(SCANNER_AUTO_RESTART_DELAY_MS)
        if (
            cameraPermission.hasPermission &&
            viewModel.uiState.value == MainUiState.Active &&
            !scannerStarted
        ) {
            startScannerIfNeeded()
        }
    }

    fun beginZoomGesture(startPosition: Offset) {
        val originalMode = scannerMode
        modeBeforeZoomGesture = originalMode
        if (
            scannerController.supportsManualModeSelection &&
            originalMode == QrScannerMode.Near
        ) {
            updateScannerMode(QrScannerMode.Far)
        }

        previewPrepared = true
        previewRevealRequested = false
        if (!startScannerIfNeeded()) return
        dragStartPosition = startPosition
        scope.launch { zoomFraction.snapTo(0f) }
        scannerController.resume()

        previewRevealJob?.cancel()
        previewRevealJob = null
        if (
            scannerController.supportsManualModeSelection &&
            originalMode == QrScannerMode.Near
        ) {
            previewRevealJob = scope.launch {
                delay(PREVIEW_REVEAL_DELAY_MS)
                previewRevealRequested = true
                previewRevealJob = null
            }
        } else {
            previewRevealRequested = true
        }
    }

    LaunchedEffect(cameraPermission.hasPermission) {
        if (cameraPermission.hasPermission) {
            hasRequestedPermission = false
            scannerAutoRestartAttempts = 0
        } else {
            if (scannerStarted) {
                scannerController.stop()
                scannerStarted = false
            }
            endZoomGesture()
            if (!hasRequestedPermission) {
                hasRequestedPermission = true
                cameraPermission.request()
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val pendingPayments by viewModel.pendingPayments.collectAsState()
    val wallets by viewModel.wallets.collectAsState()
    val contactsState by viewModel.contactsUiState.collectAsState()
    val keepPreviewWarm =
        uiState == MainUiState.Active &&
            scannerMode == QrScannerMode.Far &&
            cameraPermission.hasPermission
    val previewMounted = previewPrepared || keepPreviewWarm
    val previewVisible = previewMounted && previewRevealRequested && previewStreaming

    LaunchedEffect(uiState) {
        if (uiState == MainUiState.Active) {
            scannerAutoRestartAttempts = 0
        } else {
            endZoomGesture()
        }
    }

    LaunchedEffect(keepPreviewWarm) {
        if (!keepPreviewWarm) return@LaunchedEffect
        if (startScannerIfNeeded()) {
            scannerController.resume()
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
                onDragStart = ::beginZoomGesture,
                onDragCancel = ::endZoomGesture,
                onDragEnd = ::endZoomGesture,
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

                    val quantizedZoom = quantizeZoom(newZoom)
                    if (abs(quantizedZoom - zoomFraction.value) >= ZOOM_STEP) {
                        scope.launch { zoomFraction.snapTo(quantizedZoom) }
                    }
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
            contactsState = contactsState,
            snackbarHostState = snackbarHostState,
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
            onPendingRetrySameInvoice = {
                viewModel.dispatch(MainIntent.PendingRetrySameInvoice)
            },
            onPendingRetryCreateNewInvoice = {
                viewModel.dispatch(MainIntent.PendingRetryCreateNewInvoice)
            },
            onPendingRetryViewPending = {
                viewModel.dispatch(MainIntent.PendingRetryViewPending)
            },
            onPendingRetryDismiss = { viewModel.dispatch(MainIntent.PendingRetryDismiss) },
            onResultDismiss = { viewModel.dispatch(MainIntent.DismissResult) },
            onPendingTap = { id -> viewModel.dispatch(MainIntent.TapPending(id)) },
            onContactsOpen = { viewModel.dispatch(MainIntent.OpenContacts) },
            onContactsDismiss = { viewModel.dispatch(MainIntent.DismissContacts) },
            onPaySheetTabSelected = { tab ->
                viewModel.dispatch(MainIntent.PaySheetTabSelected(tab))
            },
            onContactsRoleSelected = { role ->
                viewModel.dispatch(MainIntent.ContactRoleSelected(role))
            },
            onShortcutSelected = { id ->
                viewModel.dispatch(MainIntent.SelectShortcut(id))
            },
            onCreateShortcut = {
                viewModel.dispatch(MainIntent.DismissContacts)
                onNavigateToPaymentSettings()
            },
            onCreateContact = {
                viewModel.dispatch(MainIntent.DismissContacts)
                onNavigateToContactsSettings()
            },
            onContactSelected = { id ->
                viewModel.dispatch(MainIntent.SelectContact(id))
            },
            onSaveContactPromptAliasChange = { alias ->
                viewModel.dispatch(MainIntent.SaveContactPromptAliasChanged(alias))
            },
            onSaveContactPromptRoleSelected = { role ->
                viewModel.dispatch(MainIntent.SaveContactPromptRoleSelected(role))
            },
            onSaveContactPromptSave = { viewModel.dispatch(MainIntent.SaveContactPromptSave) },
            onSaveContactPromptDismiss = {
                viewModel.dispatch(MainIntent.SaveContactPromptDismiss)
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
                if (!startScannerIfNeeded()) return@MainScreen
                scannerController.resume()
            },
            onScannerPause = {
                if (scannerStarted) {
                    scannerController.pause()
                }
            },
            isCameraPermissionGranted = cameraPermission.hasPermission,
            scannerMode = scannerMode,
            showScannerModeSelector = scannerController.supportsManualModeSelection,
            onToggleScannerMode = if (
                scannerController.supportsManualModeSelection &&
                uiState == MainUiState.Active
            ) {
                {
                    updateScannerMode(
                        if (scannerMode == QrScannerMode.Near) {
                            QrScannerMode.Far
                        } else {
                            QrScannerMode.Near
                        }
                    )
                }
            } else {
                null
            },
            modifier = if (previewVisible) Modifier.alpha(0.05f) else Modifier
        )

        if (previewMounted) {
            CameraPreviewHost(
                controller = scannerController,
                visible = true,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (previewVisible) 1f else 0f)
                    .zIndex(if (previewVisible) 1f else -1f),
                preferCompatibleMode = true,
                onPreviewStreamingChanged = { isStreaming ->
                    previewStreaming = isStreaming
                }
            )
        }
    }
}

private fun quantizeZoom(value: Float): Float =
    ((value.coerceIn(0f, 1f) / ZOOM_STEP).roundToInt() * ZOOM_STEP)
