package xyz.lilsus.papp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.Serializable
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.presentation.main.MainEvent
import xyz.lilsus.papp.presentation.main.MainIntent
import xyz.lilsus.papp.presentation.main.MainScreen
import xyz.lilsus.papp.presentation.main.MainViewModel
import xyz.lilsus.papp.presentation.main.MainUiState
import xyz.lilsus.papp.presentation.main.scan.rememberCameraPermissionState
import xyz.lilsus.papp.presentation.main.scan.rememberQrScannerController

@Serializable
object Pay

fun NavGraphBuilder.paymentScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToConnectWallet: (String) -> Unit = {},
) {
    composable<Pay> {
        MainScreenEntry(
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToConnectWallet = onNavigateToConnectWallet,
        )
    }
}

@Composable
private fun MainScreenEntry(
    onNavigateToSettings: () -> Unit,
    onNavigateToConnectWallet: (String) -> Unit,
) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = remember { koin.get<MainViewModel>() }
    val cameraPermission = rememberCameraPermissionState()
    val scannerController = rememberQrScannerController()
    var hasRequestedPermission by remember { mutableStateOf(false) }
    var scannerStarted by remember { mutableStateOf(false) }

    DisposableEffect(viewModel, scannerController) {
        onDispose {
            scannerController.stop()
            scannerStarted = false
            viewModel.clear()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MainEvent.ShowError -> Unit // TODO: hook up snackbar/toast presentation.
            }
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
            if (viewModel.uiState.value == MainUiState.Active) {
                startScannerIfNeeded()
                scannerController.resume()
            }
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

    val uiState by viewModel.uiState.collectAsState()
    MainScreen(
        onNavigateSettings = onNavigateToSettings,
        onNavigateConnectWallet = onNavigateToConnectWallet,
        uiState = uiState,
        onManualAmountKeyPress = { key ->
            viewModel.dispatch(MainIntent.ManualAmountKeyPress(key))
        },
        onManualAmountSubmit = { viewModel.dispatch(MainIntent.ManualAmountSubmit) },
        onManualAmountDismiss = { viewModel.dispatch(MainIntent.ManualAmountDismiss) },
        onResultDismiss = { viewModel.dispatch(MainIntent.DismissResult) },
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
    )
}
