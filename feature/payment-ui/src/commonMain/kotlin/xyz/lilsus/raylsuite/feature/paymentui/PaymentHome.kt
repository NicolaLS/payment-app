package xyz.lilsus.raylsuite.feature.paymentui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import xyz.lilsus.raylsuite.core.camera.rememberCameraPermissionState
import xyz.lilsus.raylsuite.core.camera.rememberQrScannerController

@Composable
internal fun PaymentHome(
    state: PaymentFlowState,
    messageEvents: Flow<String>,
    appTitle: String,
    estimatedFeeHint: String?,
    onIntent: (PaymentIntent) -> Unit,
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
    val snackbarHostState = remember { SnackbarHostState() }

    var hasRequestedPermission by remember { mutableStateOf(false) }
    var scannerStarted by remember { mutableStateOf(false) }
    var scannerRestartRequest by remember { mutableStateOf(0) }
    var scannerAutoRestartAttempts by remember { mutableStateOf(0) }

    val scannerShouldRun =
        screenResumed &&
            state.payment == PaymentScreenState.Active &&
            !state.contacts.isOpen &&
            cameraPermission.hasPermission

    fun requestCameraPermissionAfterScannerFailure() {
        scannerStarted = false
        scannerController.stop()
        hasRequestedPermission = true
        cameraPermission.request()
    }

    fun handleScannerUnavailable() {
        scannerStarted = false
        if (scannerAutoRestartAttempts >= MAX_SCANNER_AUTO_RESTARTS) return
        scannerAutoRestartAttempts += 1
        scannerRestartRequest += 1
    }

    fun startScannerIfNeeded(): Boolean {
        if (!scannerShouldRun) return false
        if (scannerStarted) return true
        scannerStarted =
            scannerController.start(
                onQrCodeScanned = { rawValue ->
                    scannerAutoRestartAttempts = 0
                    onIntent(PaymentIntent.QrCodeScanned(rawValue))
                },
                onCameraPermissionMissing = ::requestCameraPermissionAfterScannerFailure,
                onScannerUnavailable = ::handleScannerUnavailable
            )
        return scannerStarted
    }

    DisposableEffect(scannerController) {
        onDispose {
            scannerController.stop()
            scannerStarted = false
        }
    }

    LaunchedEffect(messageEvents) {
        messageEvents.collectLatest(snackbarHostState::showSnackbar)
    }

    LaunchedEffect(state.transactionDetailNavigationTarget) {
        state.transactionDetailNavigationTarget?.let { id ->
            onNavigateTransactionDetail(id)
            onIntent(PaymentIntent.TransactionDetailNavigationHandled(id))
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
            if (!hasRequestedPermission) {
                hasRequestedPermission = true
                cameraPermission.request()
            }
        }
    }

    LaunchedEffect(state.payment) {
        if (state.payment == PaymentScreenState.Active) {
            scannerAutoRestartAttempts = 0
        }
    }

    LaunchedEffect(scannerShouldRun) {
        if (!scannerShouldRun) {
            if (scannerStarted) {
                scannerController.stop()
                scannerStarted = false
            }
            scannerAutoRestartAttempts = 0
            return@LaunchedEffect
        }
        scannerAutoRestartAttempts = 0
        startScannerIfNeeded()
    }

    LaunchedEffect(scannerRestartRequest, scannerShouldRun) {
        if (scannerRestartRequest == 0) return@LaunchedEffect
        delay(SCANNER_AUTO_RESTART_DELAY_MS)
        if (scannerShouldRun && !scannerStarted) startScannerIfNeeded()
    }

    var contactsSwipeDragY by remember { mutableStateOf(0f) }
    val canSwipeOpenContacts =
        state.payment == PaymentScreenState.Active && !state.contacts.isOpen
    val gestureModifier =
        Modifier
            .fillMaxSize()
            .pointerInput(canSwipeOpenContacts) {
                if (!canSwipeOpenContacts) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { contactsSwipeDragY = 0f },
                    onDragEnd = { contactsSwipeDragY = 0f },
                    onDragCancel = { contactsSwipeDragY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        contactsSwipeDragY += dragAmount
                        if (contactsSwipeDragY <= CONTACTS_SWIPE_THRESHOLD) {
                            contactsSwipeDragY = 0f
                            onIntent(PaymentIntent.OpenContacts)
                        }
                    }
                )
            }

    Box(modifier = gestureModifier) {
        PaymentScreen(
            appTitle = appTitle,
            onNavigateSettings = onNavigateSettings,
            uiState = state.payment,
            sessionTransactions = state.sessionItems.map(PaymentSessionItem::reference),
            newSessionTransactionCount = state.newSessionTransactionCount,
            contactsState = state.contacts,
            snackbarHostState = snackbarHostState,
            estimatedFeeHint = estimatedFeeHint,
            onIntent = onIntent,
            onOpenTransactions = onNavigateTransactions,
            onCreateShortcut = {
                onIntent(PaymentIntent.DismissContacts)
                onNavigateShortcutCreate()
            },
            onCreateContact = {
                onIntent(PaymentIntent.DismissContacts)
                onNavigateContacts()
            }
        )
    }
}

private const val SCANNER_AUTO_RESTART_DELAY_MS = 350L
private const val MAX_SCANNER_AUTO_RESTARTS = 5
private const val CONTACTS_SWIPE_THRESHOLD = -96f
