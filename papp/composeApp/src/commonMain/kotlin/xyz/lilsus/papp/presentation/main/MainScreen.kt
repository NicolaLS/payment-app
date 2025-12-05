package xyz.lilsus.papp.presentation.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.app_name_long
import papp.composeapp.generated.resources.point_camera_message_subtitle
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.presentation.main.PendingPaymentItem
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
    pendingPayments: List<PendingPaymentItem>,
    onManualAmountKeyPress: (ManualAmountKey) -> Unit = {},
    onManualAmountPreset: (DisplayAmount) -> Unit = {},
    onManualAmountSubmit: () -> Unit = {},
    onManualAmountDismiss: () -> Unit = {},
    onConfirmPaymentSubmit: () -> Unit = {},
    onConfirmPaymentDismiss: () -> Unit = {},
    onResultDismiss: () -> Unit = {},
    onPendingNoticeDismiss: (String) -> Unit = {},
    onPendingItemClick: (String) -> Unit = {},
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

    val isDismissable = uiState is MainUiState.Success ||
        uiState is MainUiState.Error ||
        uiState is MainUiState.Pending
    val dismissAction = when (uiState) {
        is MainUiState.Pending -> if (uiState.isNotice) {
            { onPendingNoticeDismiss(uiState.info.id) }
        } else {
            onResultDismiss
        }

        else -> onResultDismiss
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = { SettingsFAB(onNavigateSettings) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .tapToDismiss(
                    enabled = isDismissable,
                    onDismiss = dismissAction
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
                when (state) {
                    is MainUiState.Success, is MainUiState.Error -> ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = state
                    )

                    is MainUiState.Pending -> ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = state
                    )

                    else -> BottomLayout(
                        title = stringResource(Res.string.app_name_long),
                        subtitle = stringResource(Res.string.point_camera_message_subtitle),
                        pendingPayments = pendingPayments,
                        onPendingClick = onPendingItemClick
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
            onRequestScannerStart = {},
            onScannerResume = {},
            onScannerPause = {},
            isCameraPermissionGranted = true
        )
    }
}
