package xyz.lilsus.raylsuite.feature.paymentui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import xyz.lilsus.raylsuite.core.camera.rememberCameraPermissionState
import xyz.lilsus.raylsuite.core.camera.rememberQrScannerController
import xyz.lilsus.raylsuite.feature.paymenthub.HubItemId
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubHostState
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubIntent
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubActions
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition

/**
 * Owns the single scanner controller and camera lifecycle for the home. Lenses receive a
 * scanner surface and UI intents; they never start or stop the camera.
 */
@Composable
internal fun PaymentHome(
    state: PaymentFlowState,
    messageEvents: Flow<String>,
    appTitle: String,
    estimatedFeeHint: String?,
    hub: PaymentHubHostState,
    lens: PaymentHubLensDefinition,
    onIntent: (PaymentIntent) -> Unit,
    onHubIntent: (PaymentHubIntent) -> Unit,
    onNavigateTransactions: () -> Unit,
    onNavigateTransactionDetail: (String) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateLibrary: () -> Unit
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
            !hub.hasModalContent &&
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
        } else if (hub.scannerRequested) {
            onHubIntent(PaymentHubIntent.DismissScanner)
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

    val currentOnIntent = rememberUpdatedState(onIntent)
    val currentOnHubIntent = rememberUpdatedState(onHubIntent)
    val currentOnNavigateLibrary = rememberUpdatedState(onNavigateLibrary)
    val hubActions =
        remember {
            object : PaymentHubActions {
                override fun selectItem(id: HubItemId) {
                    currentOnHubIntent.value(PaymentHubIntent.SelectItem(id))
                }

                override fun openGroup(id: HubItemId) {
                    currentOnHubIntent.value(PaymentHubIntent.OpenGroup(id))
                }

                override fun openLibrary() {
                    currentOnNavigateLibrary.value()
                }

                override fun submitRawPaymentInput(value: String) {
                    currentOnIntent.value(PaymentIntent.RawInputSubmitted(value))
                }

                override fun openScanner() {
                    currentOnHubIntent.value(PaymentHubIntent.OpenScanner)
                }
            }
        }

    PaymentScreen(
        appTitle = appTitle,
        onNavigateSettings = onNavigateSettings,
        uiState = state.payment,
        sessionTransactions = state.sessionItems.map(PaymentSessionItem::reference),
        newSessionTransactionCount = state.newSessionTransactionCount,
        snackbarHostState = snackbarHostState,
        estimatedFeeHint = estimatedFeeHint,
        hub = hub,
        lens = lens,
        hubActions = hubActions,
        onIntent = onIntent,
        onHubIntent = onHubIntent,
        onOpenTransactions = onNavigateTransactions,
        onOpenLibrary = onNavigateLibrary
    )
}

private const val SCANNER_AUTO_RESTART_DELAY_MS = 350L
private const val MAX_SCANNER_AUTO_RESTARTS = 5
