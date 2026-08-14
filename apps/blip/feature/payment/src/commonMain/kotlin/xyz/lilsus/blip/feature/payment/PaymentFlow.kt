package xyz.lilsus.blip.feature.payment

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.payment.components.SessionTransactionsScreen
import xyz.lilsus.blip.feature.payment.generated.resources.Res
import xyz.lilsus.blip.feature.payment.generated.resources.retry_payment
import xyz.lilsus.blip.feature.payment.generated.resources.tap_dismiss_pending
import xyz.lilsus.blip.feature.payment.generated.resources.tap_dismiss_pending_blink
import xyz.lilsus.blip.feature.payment.generated.resources.toast_bitcoin_address
import xyz.lilsus.blip.feature.payment.generated.resources.toast_bolt12_not_supported
import xyz.lilsus.raylsuite.core.camera.CameraPreviewHost
import xyz.lilsus.raylsuite.core.camera.QrScannerMode
import xyz.lilsus.raylsuite.core.camera.rememberCameraPermissionState
import xyz.lilsus.raylsuite.core.camera.rememberQrScannerController
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.feature.paymentui.components.PaymentHero
import xyz.lilsus.raylsuite.feature.paymentui.components.ResultLayout

@Composable
fun PaymentFlow(
    coordinator: PaymentCoordinator,
    appTitle: String,
    estimatedFeeHint: String?,
    errorMessageFor: @Composable (PaymentUiError) -> String,
    eventErrorMessageFor: suspend (PaymentUiError) -> String,
    onNavigateSettings: () -> Unit,
    onNavigateShortcutCreate: () -> Unit,
    onNavigateContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }
    NavHost(
        navController = navController,
        startDestination = PAYMENT_HOME_ROUTE,
        modifier = modifier
    ) {
        composable(PAYMENT_HOME_ROUTE) {
            PaymentHomeEntry(
                coordinator = coordinator,
                appTitle = appTitle,
                estimatedFeeHint = estimatedFeeHint,
                errorMessageFor = errorMessageFor,
                eventErrorMessageFor = eventErrorMessageFor,
                onNavigateTransactions = {
                    navController.navigate(PAYMENT_TRANSACTIONS_ROUTE) {
                        launchSingleTop = true
                    }
                },
                onNavigateTransactionDetail = { id ->
                    selectedTransactionId = id
                    navController.navigate(PAYMENT_DETAIL_ROUTE)
                },
                onNavigateSettings = onNavigateSettings,
                onNavigateShortcutCreate = onNavigateShortcutCreate,
                onNavigateContacts = onNavigateContacts
            )
        }
        composable(PAYMENT_TRANSACTIONS_ROUTE) {
            PaymentTransactionsEntry(
                coordinator = coordinator,
                onBack = navController::navigateUp,
                onTransactionSelected = { id ->
                    selectedTransactionId = id
                    navController.navigate(PAYMENT_DETAIL_ROUTE)
                }
            )
        }
        composable(PAYMENT_DETAIL_ROUTE) {
            PaymentTransactionDetailEntry(
                coordinator = coordinator,
                transactionId = selectedTransactionId,
                estimatedFeeHint = estimatedFeeHint,
                errorMessageFor = errorMessageFor,
                onBack = navController::navigateUp
            )
        }
    }
}

@Composable
private fun PaymentHomeEntry(
    coordinator: PaymentCoordinator,
    appTitle: String,
    estimatedFeeHint: String?,
    errorMessageFor: @Composable (PaymentUiError) -> String,
    eventErrorMessageFor: suspend (PaymentUiError) -> String,
    onNavigateTransactions: () -> Unit,
    onNavigateTransactionDetail: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateShortcutCreate: () -> Unit,
    onNavigateContacts: () -> Unit
) {
    val cameraPermission = rememberCameraPermissionState()
    val scannerController = rememberQrScannerController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val screenResumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
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
    var zoomGestureActive by remember { mutableStateOf(false) }
    var previewRevealJob by remember { mutableStateOf<Job?>(null) }
    var scannerRestartRequest by remember { mutableStateOf(0) }
    var scannerAutoRestartAttempts by remember { mutableStateOf(0) }
    val zoomFraction = remember { Animatable(0f) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }

    val uiState by coordinator.uiState.collectAsState()
    val sessionTransactions by coordinator.sessionTransactions.collectAsState()
    val newSessionTransactionCount by coordinator.newSessionTransactionCount.collectAsState()
    val transactionDetailNavigationTarget by
        coordinator.transactionDetailNavigationTarget.collectAsState()
    val contactsState by coordinator.contactsState.collectAsState()
    val scannerShouldRun =
        screenResumed &&
            uiState == PaymentUiState.Active &&
            !contactsState.isOpen &&
            cameraPermission.hasPermission
    val canSelectScannerMode =
        scannerController.supportsManualModeSelection && scannerShouldRun
    val keepPreviewWarm = scannerShouldRun && scannerMode == QrScannerMode.Far
    val previewMounted = previewPrepared || keepPreviewWarm
    val previewVisible = previewMounted && previewRevealRequested && previewStreaming

    fun hidePreview() {
        previewRevealJob?.cancel()
        previewRevealJob = null
        previewRevealRequested = false
        previewPrepared = false
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

    fun endZoomGesture() {
        zoomGestureActive = false
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
        if (!scannerShouldRun) return false
        if (scannerStarted) return true
        scannerController.setMode(scannerMode)
        scannerStarted =
            scannerController.start(
                onQrCodeScanned = { rawValue ->
                    scannerAutoRestartAttempts = 0
                    coordinator.dispatch(PaymentIntent.QrCodeScanned(rawValue))
                },
                onCameraPermissionMissing = ::requestCameraPermissionAfterScannerFailure,
                onScannerUnavailable = ::handleScannerUnavailable
            )
        return scannerStarted
    }

    fun beginZoomGesture(startPosition: Offset) {
        zoomGestureActive = true
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
        if (!startScannerIfNeeded()) {
            zoomGestureActive = false
            return
        }
        dragStartPosition = startPosition
        scope.launch { zoomFraction.snapTo(0f) }
        scannerController.resume()
        previewRevealJob?.cancel()
        previewRevealJob =
            if (
                scannerController.supportsManualModeSelection &&
                originalMode == QrScannerMode.Near
            ) {
                scope.launch {
                    delay(PREVIEW_REVEAL_DELAY_MS)
                    previewRevealRequested = true
                    previewRevealJob = null
                }
            } else {
                previewRevealRequested = true
                null
            }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { zoomFraction.value }
            .map(::quantizeZoom)
            .distinctUntilChanged()
            .collect(scannerController::setZoom)
    }

    DisposableEffect(coordinator, scannerController) {
        onDispose {
            previewRevealJob?.cancel()
            scannerController.stop()
            scannerStarted = false
            previewPrepared = false
            previewRevealRequested = false
            previewStreaming = false
        }
    }

    LaunchedEffect(coordinator) {
        coordinator.events.collectLatest { event ->
            val message =
                when (event) {
                    is PaymentEvent.ShowError -> eventErrorMessageFor(event.error)

                    is PaymentEvent.ShowToast ->
                        when (event.message) {
                            PaymentToastMessage.BitcoinAddressNotSupported ->
                                getString(Res.string.toast_bitcoin_address)

                            PaymentToastMessage.Bolt12NotSupported ->
                                getString(Res.string.toast_bolt12_not_supported)
                        }
                }
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(transactionDetailNavigationTarget) {
        transactionDetailNavigationTarget?.let { id ->
            onNavigateTransactionDetail(id)
            coordinator.dispatch(PaymentIntent.TransactionDetailNavigationHandled(id))
        }
    }

    LaunchedEffect(screenResumed, cameraPermission.hasPermission) {
        if (!screenResumed) return@LaunchedEffect
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

    LaunchedEffect(uiState) {
        if (uiState == PaymentUiState.Active) {
            scannerAutoRestartAttempts = 0
        } else {
            endZoomGesture()
        }
    }

    LaunchedEffect(scannerShouldRun) {
        if (!scannerShouldRun) {
            if (scannerStarted) {
                scannerController.stop()
                scannerStarted = false
            }
            scannerAutoRestartAttempts = 0
            endZoomGesture()
            return@LaunchedEffect
        }
        scannerAutoRestartAttempts = 0
        if (startScannerIfNeeded()) scannerController.resume()
    }

    LaunchedEffect(scannerRestartRequest, scannerShouldRun) {
        if (scannerRestartRequest == 0) return@LaunchedEffect
        delay(SCANNER_AUTO_RESTART_DELAY_MS)
        if (scannerShouldRun && !scannerStarted && startScannerIfNeeded()) {
            scannerController.resume()
        }
    }

    var contactsSwipeDragY by remember { mutableStateOf(0f) }
    val canSwipeOpenContacts = uiState == PaymentUiState.Active && !contactsState.isOpen
    val gestureModifier =
        Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(canSwipeOpenContacts, zoomGestureActive) {
                if (!canSwipeOpenContacts) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { contactsSwipeDragY = 0f },
                    onDragEnd = { contactsSwipeDragY = 0f },
                    onDragCancel = { contactsSwipeDragY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (zoomGestureActive) return@detectVerticalDragGestures
                        contactsSwipeDragY += dragAmount
                        if (contactsSwipeDragY <= CONTACTS_SWIPE_THRESHOLD) {
                            contactsSwipeDragY = 0f
                            coordinator.dispatch(PaymentIntent.OpenContacts)
                        }
                    }
                )
            }.pointerInput(scannerShouldRun) {
                if (!scannerShouldRun) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = ::beginZoomGesture,
                    onDragCancel = ::endZoomGesture,
                    onDragEnd = ::endZoomGesture,
                    onDrag = { change, _ ->
                        val height =
                            containerSize.height
                                .toFloat()
                                .takeIf { it > 0f }
                                ?: return@detectDragGesturesAfterLongPress
                        val dragDistance = change.position.y - dragStartPosition.y
                        val newZoom =
                            (dragDistance / (height * ZOOM_DRAG_RANGE)).coerceIn(0f, 1f)
                        val quantizedZoom = quantizeZoom(newZoom)
                        if (abs(quantizedZoom - zoomFraction.value) >= ZOOM_STEP) {
                            scope.launch { zoomFraction.snapTo(quantizedZoom) }
                        }
                    }
                )
            }

    Box(modifier = gestureModifier) {
        PaymentScreen(
            appTitle = appTitle,
            onNavigateSettings = onNavigateSettings,
            uiState = uiState,
            sessionTransactions = sessionTransactions,
            newSessionTransactionCount = newSessionTransactionCount,
            contactsState = contactsState,
            snackbarHostState = snackbarHostState,
            errorMessageFor = errorMessageFor,
            estimatedFeeHint = estimatedFeeHint,
            onManualAmountKeyPress = {
                coordinator.dispatch(PaymentIntent.ManualAmountKeyPress(it))
            },
            onManualAmountPreset = {
                coordinator.dispatch(PaymentIntent.ManualAmountPreset(it))
            },
            onManualAmountSubmit = {
                coordinator.dispatch(PaymentIntent.ManualAmountSubmit)
            },
            onManualAmountDismiss = {
                coordinator.dispatch(PaymentIntent.ManualAmountDismiss)
            },
            onConfirmPaymentSubmit = {
                coordinator.dispatch(PaymentIntent.ConfirmPaymentSubmit)
            },
            onConfirmPaymentDismiss = {
                coordinator.dispatch(PaymentIntent.ConfirmPaymentDismiss)
            },
            onPendingRetryCreateNewInvoice = {
                coordinator.dispatch(PaymentIntent.PendingRetryCreateNewInvoice)
            },
            onPendingRetryRetryPrevious = {
                coordinator.dispatch(PaymentIntent.PendingRetryRetryPrevious)
            },
            onPendingRetryViewPending = {
                coordinator.dispatch(PaymentIntent.PendingRetryViewPending)
            },
            onPendingRetryDismiss = {
                coordinator.dispatch(PaymentIntent.PendingRetryDismiss)
            },
            onOpenTransactions = onNavigateTransactions,
            onResultDismiss = {
                coordinator.dispatch(PaymentIntent.DismissResult)
            },
            onContactsOpen = {
                coordinator.dispatch(PaymentIntent.OpenContacts)
            },
            onContactsDismiss = {
                coordinator.dispatch(PaymentIntent.DismissContacts)
            },
            onPaymentSheetTabSelected = {
                coordinator.dispatch(PaymentIntent.PaymentSheetTabSelected(it))
            },
            onContactsRoleSelected = {
                coordinator.dispatch(PaymentIntent.ContactRoleSelected(it))
            },
            onShortcutSelected = {
                coordinator.dispatch(PaymentIntent.SelectShortcut(it))
            },
            onCreateShortcut = {
                coordinator.dispatch(PaymentIntent.DismissContacts)
                onNavigateShortcutCreate()
            },
            onCreateContact = {
                coordinator.dispatch(PaymentIntent.DismissContacts)
                onNavigateContacts()
            },
            onContactSelected = {
                coordinator.dispatch(PaymentIntent.SelectContact(it))
            },
            onSaveContactPromptAliasChange = {
                coordinator.dispatch(PaymentIntent.SaveContactPromptAliasChanged(it))
            },
            onSaveContactPromptRoleSelected = {
                coordinator.dispatch(PaymentIntent.SaveContactPromptRoleSelected(it))
            },
            onSaveContactPromptSave = {
                coordinator.dispatch(PaymentIntent.SaveContactPromptSave)
            },
            onSaveContactPromptDismiss = {
                coordinator.dispatch(PaymentIntent.SaveContactPromptDismiss)
            },
            scannerMode = scannerMode,
            showScannerModeSelector = canSelectScannerMode,
            onToggleScannerMode =
                if (canSelectScannerMode) {
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
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(if (previewVisible) 1f else 0f)
                        .zIndex(if (previewVisible) 1f else -1f),
                preferCompatibleMode = true,
                onPreviewStreamingChanged = { previewStreaming = it }
            )
        }
    }
}

@Composable
private fun PaymentTransactionsEntry(
    coordinator: PaymentCoordinator,
    onBack: () -> Unit,
    onTransactionSelected: (String) -> Unit
) {
    val transactions by coordinator.sessionTransactions.collectAsState()
    LaunchedEffect(coordinator) {
        coordinator.dispatch(PaymentIntent.SessionTransactionsOpened)
    }
    SessionTransactionsScreen(
        modifier = Modifier.fillMaxSize(),
        transactions = transactions,
        onBack = onBack,
        onTransactionSelected = onTransactionSelected
    )
}

@Composable
private fun PaymentTransactionDetailEntry(
    coordinator: PaymentCoordinator,
    transactionId: String?,
    estimatedFeeHint: String?,
    errorMessageFor: @Composable (PaymentUiError) -> String,
    onBack: () -> Unit
) {
    val transactions by coordinator.sessionTransactions.collectAsState()
    val transaction = transactions.firstOrNull { it.id == transactionId }
    LaunchedEffect(transactionId, transaction) {
        if (transaction == null) onBack()
    }
    DisposableEffect(coordinator, transactionId) {
        onDispose { coordinator.dispatch(PaymentIntent.DismissResult) }
    }
    transaction?.let {
        PaymentTransactionDetailScreen(
            transaction = it,
            estimatedFeeHint = estimatedFeeHint,
            errorMessageFor = errorMessageFor,
            onRetry = {
                coordinator.dispatch(PaymentIntent.RetryTransaction(it.id))
                onBack()
            },
            onDismiss = onBack
        )
    }
}

@Composable
private fun PaymentTransactionDetailScreen(
    transaction: SessionTransactionItem,
    estimatedFeeHint: String?,
    errorMessageFor: @Composable (PaymentUiError) -> String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val detailState = transaction.toDetailUiState()
    val receiptPreimage =
        (detailState as? PaymentUiState.Success)
            ?.preimage
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    var showReceipt by remember { mutableStateOf(false) }
    LaunchedEffect(receiptPreimage) {
        showReceipt = false
    }
    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .clickable(indication = null, interactionSource = null) { onDismiss() }
                    .fillMaxSize()
                    .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PaymentHero(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                phase = detailState.toHeroPhase(),
                receiptPreimage = receiptPreimage.takeIf { showReceipt }
            )
            when (detailState) {
                is PaymentUiState.Success,
                is PaymentUiState.Error ->
                    ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = detailState.toResultPresentation(errorMessageFor),
                        receiptVisible = showReceipt,
                        estimatedFeeHint = estimatedFeeHint,
                        onViewReceipt = { showReceipt = true },
                        actionLabel =
                            if (transaction.status == PendingStatus.StatusUnknown) {
                                stringResource(Res.string.retry_payment)
                            } else {
                                null
                            },
                        onAction = onRetry
                    )

                else ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (transaction.status == PendingStatus.PendingInBlink) {
                                        Res.string.tap_dismiss_pending_blink
                                    } else {
                                        Res.string.tap_dismiss_pending
                                    }
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
            }
        }
    }
}

private fun SessionTransactionItem.toDetailUiState(): PaymentUiState = when (status) {
    PendingStatus.Sending,
    PendingStatus.PendingInBlink -> PaymentUiState.Loading()

    PendingStatus.Success,
    PendingStatus.AlreadyPaid -> {
        val paidAmount = resultAmount ?: amount
        PaymentUiState.Success(
            amountPaid = paidAmount,
            feePaid = fee ?: paidAmount.zero(),
            showEstimatedFeeHint = showEstimatedFeeHint && !wasAlreadyPaid,
            wasAlreadyPaid = status == PendingStatus.AlreadyPaid || wasAlreadyPaid,
            preimage = preimage
        )
    }

    PendingStatus.Failure,
    PendingStatus.StatusUnknown ->
        PaymentUiState.Error(
            error ?: PaymentUiError.Unexpected(errorMessage)
        )
}

private fun DisplayAmount.zero(): DisplayAmount = DisplayAmount(0, currency)

private fun quantizeZoom(value: Float): Float =
    ((value.coerceIn(0f, 1f) / ZOOM_STEP).roundToInt() * ZOOM_STEP)

private const val PAYMENT_HOME_ROUTE = "payment/home"
private const val PAYMENT_TRANSACTIONS_ROUTE = "payment/transactions"
private const val PAYMENT_DETAIL_ROUTE = "payment/detail"
private const val ZOOM_DRAG_RANGE = 0.4f
private const val ZOOM_STEP = 0.01f
private const val PREVIEW_REVEAL_DELAY_MS = 220L
private const val SCANNER_AUTO_RESTART_DELAY_MS = 350L
private const val MAX_SCANNER_AUTO_RESTARTS = 5
private const val CONTACTS_SWIPE_THRESHOLD = -96f
