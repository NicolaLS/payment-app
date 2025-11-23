package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.settings_payments
import papp.composeapp.generated.resources.settings_payments_confirm_label
import papp.composeapp.generated.resources.settings_payments_confirm_manual_entry
import papp.composeapp.generated.resources.settings_payments_confirm_threshold
import papp.composeapp.generated.resources.settings_payments_haptics_payment
import papp.composeapp.generated.resources.settings_payments_haptics_scan
import papp.composeapp.generated.resources.settings_payments_haptics_title
import papp.composeapp.generated.resources.settings_payments_option_above
import papp.composeapp.generated.resources.settings_payments_option_always
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.domain.model.PaymentPreferences
import xyz.lilsus.papp.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentsSettingsScreen(
    state: PaymentsSettingsUiState,
    onBack: () -> Unit,
    onModeSelected: (PaymentConfirmationMode) -> Unit,
    onThresholdChanged: (Long) -> Unit,
    onConfirmManualEntryChanged: (Boolean) -> Unit,
    onVibrateOnScanChanged: (Boolean) -> Unit,
    onVibrateOnPaymentChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollState = rememberScrollState()
    val formatter = rememberAmountFormatter()
    val displayThreshold = DisplayAmount(state.thresholdSats, DisplayCurrency.Satoshi)
    val thresholdText = when (state.confirmationMode) {
        PaymentConfirmationMode.Above -> stringResource(
            Res.string.settings_payments_confirm_threshold,
            formatter.format(displayThreshold)
        )

        PaymentConfirmationMode.Always -> stringResource(Res.string.settings_payments_option_always)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_payments)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.settings_payments_confirm_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = thresholdText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PaymentModeChips(selected = state.confirmationMode, onSelected = onModeSelected)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_payments_confirm_manual_entry),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        )
                        Switch(
                            checked = state.confirmManualEntry,
                            onCheckedChange = onConfirmManualEntryChanged,
                        )
                    }
                    if (state.confirmationMode == PaymentConfirmationMode.Above) {
                        Slider(
                            value = state.thresholdSats.toFloat(),
                            onValueChange = { onThresholdChanged(it.toLong()) },
                            valueRange = state.minThreshold.toFloat()..state.maxThreshold.toFloat(),
                        )
                    } else {
                        // keep layout height consistent
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.settings_payments_haptics_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_payments_haptics_scan),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        )
                        Switch(
                            checked = state.vibrateOnScan,
                            onCheckedChange = onVibrateOnScanChanged,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_payments_haptics_payment),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        )
                        Switch(
                            checked = state.vibrateOnPayment,
                            onCheckedChange = onVibrateOnPaymentChanged,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentModeChips(
    selected: PaymentConfirmationMode,
    onSelected: (PaymentConfirmationMode) -> Unit,
) {
    val options = listOf(
        PaymentConfirmationMode.Always to Res.string.settings_payments_option_always,
        PaymentConfirmationMode.Above to Res.string.settings_payments_option_above,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEach { (mode, labelRes) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Preview
@Composable
private fun PaymentsSettingsScreenPreview() {
    AppTheme {
        PaymentsSettingsScreen(
            state = PaymentsSettingsUiState(
                confirmationMode = PaymentConfirmationMode.Above,
                thresholdSats = PaymentPreferences.DEFAULT_CONFIRMATION_THRESHOLD_SATS,
                confirmManualEntry = true,
                vibrateOnScan = true,
                vibrateOnPayment = true,
            ),
            onBack = {},
            onModeSelected = {},
            onThresholdChanged = {},
            onConfirmManualEntryChanged = {},
            onVibrateOnScanChanged = {},
            onVibrateOnPaymentChanged = {},
        )
    }
}
