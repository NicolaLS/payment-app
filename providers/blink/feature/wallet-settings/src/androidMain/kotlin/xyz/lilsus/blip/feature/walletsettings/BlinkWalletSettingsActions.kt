package xyz.lilsus.blip.feature.walletsettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.lilsus.blip.feature.walletsettings.R
import xyz.lilsus.blip.integration.blink.BlinkFundingWallet
import xyz.lilsus.blip.integration.blink.BlinkWalletCurrency
import xyz.lilsus.blip.ui.blinkErrorMessageFor
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListRow

/** Android renderer for Blip's wallet settings actions. */
@Composable
fun BlinkWalletSettingsActions(
    state: BlinkWalletSettingsUiState,
    onLoadFundingWallets: () -> Unit,
    onSelectFundingWallet: (String) -> Unit,
    onRemoveWallet: () -> Unit,
    modifier: Modifier = Modifier,
    canRemove: Boolean = true,
    removalMessage: String? = null
) {
    var showFundingWallets by remember { mutableStateOf(false) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppListDefaults.SectionSpacing)
    ) {
        AppListRow(
            onClick = {
                showFundingWallets = true
                onLoadFundingWallets()
            },
            testTag = BlinkWalletSettingsTestTags.FUNDING_WALLET_BUTTON,
            minHeight = 48.dp,
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_blink_funding_wallet),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text =
                        state.selectedWallet?.currency?.fundingWalletTitle()
                            ?: stringResource(R.string.settings_blink_funding_wallet_choose),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppListRow(
            onClick = { showRemoveConfirmation = true },
            testTag = BlinkWalletSettingsTestTags.REMOVE_BUTTON,
            minHeight = 48.dp,
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.error
        ) {
            Text(
                text = stringResource(R.string.settings_blink_connection_remove),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null
            )
        }
    }

    if (showFundingWallets) {
        FundingWalletDialog(
            state = state,
            onSelect = { wallet ->
                onSelectFundingWallet(wallet.id)
                showFundingWallets = false
            },
            onDismiss = { showFundingWallets = false }
        )
    }

    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = {
                Text(stringResource(R.string.settings_blink_connection_remove_title))
            },
            text = {
                Text(
                    removalMessage ?: stringResource(
                        R.string.settings_blink_connection_remove_description,
                        xyz.lilsus.raylsuite.core.ui.platform.LocalProductName.current
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = canRemove,
                    onClick = {
                        showRemoveConfirmation = false
                        onRemoveWallet()
                    }
                ) {
                    Text(
                        text =
                            stringResource(
                                R.string.settings_blink_connection_remove_confirm
                            ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false }) {
                    Text(
                        stringResource(
                            R.string.settings_blink_connection_remove_cancel
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun FundingWalletDialog(
    state: BlinkWalletSettingsUiState,
    onSelect: (BlinkFundingWallet) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_blink_funding_wallet_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.isLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Text(stringResource(R.string.settings_blink_funding_wallet_loading))
                    }
                }
                if (state.selectionUnavailable) {
                    Text(
                        text =
                            stringResource(
                                R.string.settings_blink_funding_wallet_unavailable
                            ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                state.error?.let { error ->
                    Text(
                        text = blinkErrorMessageFor(error),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (!state.isLoading) {
                    state.wallets.forEach { wallet ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(wallet) }
                                    .testTag(
                                        BlinkWalletSettingsTestTags.walletOption(wallet.currency)
                                    ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.selectedWallet?.id == wallet.id,
                                onClick = { onSelect(wallet) }
                            )
                            Text(wallet.currency.fundingWalletTitle())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_blink_funding_wallet_close))
            }
        }
    )
}

@Composable
private fun BlinkWalletCurrency.fundingWalletTitle(): String = stringResource(
    when (this) {
        BlinkWalletCurrency.BTC -> R.string.settings_blink_funding_wallet_bitcoin
        BlinkWalletCurrency.USD -> R.string.settings_blink_funding_wallet_stablesats
    }
)

object BlinkWalletSettingsTestTags {
    const val FUNDING_WALLET_BUTTON = "settings_blink_funding_wallet"
    const val REMOVE_BUTTON = "settings_blink_connection_remove"

    fun walletOption(currency: BlinkWalletCurrency): String =
        "settings_blink_funding_wallet_${currency.name.lowercase()}"
}
