package xyz.lilsus.papp.presentation.main

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.app_name_long
import papp.composeapp.generated.resources.point_camera_message_subtitle
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.presentation.main.components.BottomLayout
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
    onManualAmountKeyPress: (ManualAmountKey) -> Unit = {},
    onManualAmountSubmit: () -> Unit = {},
    onManualAmountDismiss: () -> Unit = {},
    onResultDismiss: () -> Unit = {},
    onRequestScannerStart: () -> Unit,
    onScannerResume: () -> Unit,
    onScannerPause: () -> Unit,
    lastScannedInvoice: String? = null,
    isCameraPermissionGranted: Boolean,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        onRequestScannerStart()
    }

    LaunchedEffect(isCameraPermissionGranted) {
        if (isCameraPermissionGranted) {
            onRequestScannerStart()
        } else {
            onScannerPause()
        }
    }

    LaunchedEffect(uiState, isCameraPermissionGranted) {
        if (!isCameraPermissionGranted) return@LaunchedEffect
        when (uiState) {
            MainUiState.Active -> onScannerResume()
            else -> onScannerPause()
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = { SettingsFAB(onNavigateSettings) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .tapToDismiss(
                    enabled = uiState is MainUiState.Success || uiState is MainUiState.Error,
                    onDismiss = onResultDismiss
                )
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Hero(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                uiState = uiState,
            )
            Crossfade(targetState = uiState) { state ->
                when (state) {
                    is MainUiState.Success, is MainUiState.Error -> ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = state
                    )

                    else -> BottomLayout(
                        title = stringResource(Res.string.app_name_long),
                        subtitle = stringResource(Res.string.point_camera_message_subtitle)
                    )
                }
            }
            lastScannedInvoice?.let { scanned ->
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = scanned,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (uiState is MainUiState.EnterAmount) {
        ManualAmountBottomSheet(
            state = uiState.entry,
            onKeyPress = onManualAmountKeyPress,
            onSubmit = onManualAmountSubmit,
            onDismiss = onManualAmountDismiss
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
            uiState = MainUiState.Success(DisplayAmount(69, DisplayCurrency.Satoshi)),
            onRequestScannerStart = {},
            onScannerResume = {},
            onScannerPause = {},
            lastScannedInvoice = "lnbc1...",
            isCameraPermissionGranted = true,
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
            onRequestScannerStart = {},
            onScannerResume = {},
            onScannerPause = {},
            isCameraPermissionGranted = true,
        )
    }
}
