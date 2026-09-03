package xyz.lilsus.blip.feature.walletsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.walletsettings.generated.resources.Res
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_refresh
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_refresh_success
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove_cancel
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove_confirm
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove_description
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove_title
import xyz.lilsus.blip.ui.blinkErrorMessageFor
import xyz.lilsus.raylsuite.core.ui.components.AppListDefaults
import xyz.lilsus.raylsuite.core.ui.components.AppListRow

/** Android renderer for Blip's wallet settings actions. */
@Composable
fun BlinkWalletSettingsActions(
    state: BlinkWalletSettingsUiState,
    onRefreshConnection: () -> Unit,
    onRemoveWallet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showRemoveConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppListDefaults.SectionSpacing)
    ) {
        AppListRow(
            onClick = onRefreshConnection,
            enabled = !state.isRefreshing,
            testTag = BlinkWalletSettingsTestTags.REFRESH_BUTTON,
            minHeight = 48.dp,
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            RefreshConnectionContent(
                text = stringResource(Res.string.settings_blink_connection_refresh),
                state = state,
                modifier = Modifier.weight(1f)
            )
            if (state.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                text = stringResource(Res.string.settings_blink_connection_remove),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null
            )
        }
    }

    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = {
                Text(stringResource(Res.string.settings_blink_connection_remove_title))
            },
            text = {
                Text(
                    stringResource(
                        Res.string.settings_blink_connection_remove_description
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirmation = false
                        onRemoveWallet()
                    }
                ) {
                    Text(
                        text =
                            stringResource(
                                Res.string.settings_blink_connection_remove_confirm
                            ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false }) {
                    Text(
                        stringResource(
                            Res.string.settings_blink_connection_remove_cancel
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun RefreshConnectionContent(
    text: String,
    state: BlinkWalletSettingsUiState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
        when {
            state.refreshSucceeded -> {
                Text(
                    text = stringResource(Res.string.settings_blink_connection_refresh_success),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            state.error != null -> {
                Text(
                    text = blinkErrorMessageFor(state.error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

object BlinkWalletSettingsTestTags {
    const val REFRESH_BUTTON = "settings_blink_connection_refresh"
    const val REMOVE_BUTTON = "settings_blink_connection_remove"
}
