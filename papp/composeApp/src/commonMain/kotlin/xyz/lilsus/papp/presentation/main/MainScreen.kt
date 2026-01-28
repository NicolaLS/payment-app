package xyz.lilsus.papp.presentation.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.app_name_long
import papp.composeapp.generated.resources.point_camera_message_subtitle
import papp.composeapp.generated.resources.tap_dismiss_pending
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.presentation.main.components.BottomLayout
import xyz.lilsus.papp.presentation.main.components.ConfirmationBottomSheet
import xyz.lilsus.papp.presentation.main.components.ManualAmountBottomSheet
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey
import xyz.lilsus.papp.presentation.main.components.ManualAmountUiState
import xyz.lilsus.papp.presentation.main.components.ResultLayout
import xyz.lilsus.papp.presentation.main.components.SettingsFAB
import xyz.lilsus.papp.presentation.main.components.hero.Hero
import xyz.lilsus.papp.presentation.theme.AppTheme

@Composable
fun MainScreen(
    onNavigateSettings: () -> Unit,
    onNavigateConnectWallet: (String) -> Unit,
    uiState: MainUiState,
    wallets: List<WalletInfo> = emptyList(),
    pendingPayments: List<PendingPaymentItem>,
    snackbarHostState: SnackbarHostState,
    onManualAmountKeyPress: (ManualAmountKey) -> Unit = {},
    onManualAmountPreset: (DisplayAmount) -> Unit = {},
    onManualAmountSubmit: () -> Unit = {},
    onManualAmountDismiss: () -> Unit = {},
    onConfirmPaymentSubmit: () -> Unit = {},
    onConfirmPaymentDismiss: () -> Unit = {},
    onResultDismiss: () -> Unit = {},
    onPendingTap: (String) -> Unit = {},
    onRequestScannerStart: () -> Unit,
    onScannerResume: () -> Unit,
    onScannerPause: () -> Unit,
    isCameraPermissionGranted: Boolean,
    modifier: Modifier = Modifier
) {
    var scannerInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(uiState, isCameraPermissionGranted) {
        if (!isCameraPermissionGranted) {
            scannerInitialized = false
            onScannerPause()
        } else {
            when (uiState) {
                MainUiState.Active -> {
                    if (!scannerInitialized) {
                        withFrameNanos { }
                        onRequestScannerStart()
                        scannerInitialized = true
                    }
                    onScannerResume()
                }

                else -> onScannerPause()
            }
        }
    }

    val isWatchingPending = uiState is MainUiState.Loading && uiState.isWatchingPending
    val isDismissable = uiState is MainUiState.Success ||
        uiState is MainUiState.Error ||
        isWatchingPending

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        },
        floatingActionButton = { SettingsFAB(onNavigateSettings) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .tapToDismiss(
                    enabled = isDismissable,
                    onDismiss = onResultDismiss
                )
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Hero(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                uiState = uiState
            )
            Crossfade(targetState = uiState) { state ->
                when {
                    state is MainUiState.Success || state is MainUiState.Error -> ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = state
                    )

                    state is MainUiState.Loading && state.isWatchingPending -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = stringResource(Res.string.tap_dismiss_pending),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> BottomLayout(
                        title = stringResource(Res.string.app_name_long),
                        subtitle = stringResource(Res.string.point_camera_message_subtitle),
                        wallets = wallets,
                        pendingPayments = pendingPayments,
                        onPendingTap = onPendingTap
                    )
                }
            }
        }
    }

    if (uiState is MainUiState.EnterAmount) {
        ManualAmountBottomSheet(
            state = uiState.entry,
            onKeyPress = onManualAmountKeyPress,
            onRangeClick = onManualAmountPreset,
            onSubmit = onManualAmountSubmit,
            onDismiss = onManualAmountDismiss
        )
    }

    if (uiState is MainUiState.Confirm) {
        ConfirmationBottomSheet(
            confirmAmount = uiState.amount,
            onPay = onConfirmPaymentSubmit,
            onDismiss = onConfirmPaymentDismiss
        )
    }
}

fun Modifier.tapToDismiss(enabled: Boolean, onDismiss: () -> Unit) = clickable(
    enabled = enabled,
    indication = null,
    interactionSource = null
) { onDismiss() }

@Preview
@Composable
fun MainScreenPreviewSuccess() {
    AppTheme {
        MainScreen(
            onNavigateSettings = {},
            onNavigateConnectWallet = {},
            uiState = MainUiState.Success(
                amountPaid = DisplayAmount(12345, DisplayCurrency.Satoshi),
                feePaid = DisplayAmount(69, DisplayCurrency.Satoshi)
            ),
            pendingPayments = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onRequestScannerStart = {},
            onScannerResume = {},
            onScannerPause = {},
            isCameraPermissionGranted = true
        )
    }
}

@Preview
@Composable
fun MainScreenPreviewEnterAmount() {
    AppTheme {
        MainScreen(
            onNavigateSettings = {},
            onNavigateConnectWallet = {},
            uiState = MainUiState.EnterAmount(
                entry = ManualAmountUiState(
                    amount = DisplayAmount(123, DisplayCurrency.Satoshi),
                    currency = DisplayCurrency.Satoshi,
                    min = DisplayAmount(10, DisplayCurrency.Satoshi),
                    max = DisplayAmount(1000, DisplayCurrency.Satoshi),
                    allowDecimal = false
                )
            ),
            pendingPayments = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onRequestScannerStart = {},
            onScannerResume = {},
            onScannerPause = {},
            isCameraPermissionGranted = true
        )
    }
}

@Preview
@Composable
fun MainScreenPreviewConfirm() {
    AppTheme {
        MainScreen(
            onNavigateSettings = {},
            onNavigateConnectWallet = {},
            uiState = MainUiState.Confirm(
                amount = DisplayAmount(500_000, DisplayCurrency.Satoshi)
            ),
            pendingPayments = emptyList(),
            snackbarHostState = remember { SnackbarHostState() },
            onRequestScannerStart = {},
            onScannerResume = {},
            onScannerPause = {},
            isCameraPermissionGranted = true
        )
    }
}
