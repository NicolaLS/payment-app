package xyz.lilsus.lasr.feature.walletconnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.Res
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_alias_label
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_cancel
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_confirm
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_description
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_encryption
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_encryption_active
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_lud16
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_methods
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_pubkey
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_details_relay
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_loading
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_retry
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_title
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_heading
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_legacy_nip04
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_legacy_nip04_default
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_missing_nip44
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_warning_missing_pay_invoice
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.keyboard_done
import xyz.lilsus.lasr.integration.nwc.NwcWalletDiscovery
import xyz.lilsus.lasr.ui.lasrConnectionErrorMessageFor
import xyz.lilsus.raylsuite.core.ui.keyboard.doneKeyboardPlatformImeOptions

@Composable
fun ConnectNwcWalletDialog(
    state: ConnectNwcWalletUiState,
    onAliasChange: (String) -> Unit,
    onRetryDiscovery: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.testTag(NwcWalletConnectionTestTags.CONFIRM_DIALOG),
        onDismissRequest = onCancel,
        title = { Text(stringResource(Res.string.connect_wallet_title)) },
        text = {
            ConnectDialogContent(
                state = state,
                onAliasChange = onAliasChange,
                onRetryDiscovery = onRetryDiscovery
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled =
                    state.discovery != null &&
                        !state.isSaving &&
                        !state.isDiscoveryLoading,
                modifier =
                    Modifier.testTag(
                        NwcWalletConnectionTestTags.DIALOG_CONFIRM_BUTTON
                    )
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(stringResource(Res.string.connect_wallet_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier =
                    Modifier.testTag(
                        NwcWalletConnectionTestTags.DIALOG_CANCEL_BUTTON
                    )
            ) {
                Text(stringResource(Res.string.connect_wallet_cancel))
            }
        }
    )
}

@Composable
private fun ConnectDialogContent(
    state: ConnectNwcWalletUiState,
    onAliasChange: (String) -> Unit,
    onRetryDiscovery: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(Res.string.connect_wallet_description))
        state.discovery?.let { discovery ->
            WarningSection(discovery)
        }

        when {
            state.isDiscoveryLoading -> DiscoveryLoading()

            state.discovery != null ->
                DiscoveryDetails(
                    state = state,
                    onAliasChange = onAliasChange
                )
        }

        state.error?.let { error ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = lasrConnectionErrorMessageFor(error),
                    color = MaterialTheme.colorScheme.error
                )
                if (!state.isDiscoveryLoading && state.discovery == null) {
                    TextButton(
                        onClick = onRetryDiscovery,
                        modifier =
                            Modifier.testTag(
                                NwcWalletConnectionTestTags.DIALOG_RETRY_BUTTON
                            )
                    ) {
                        Text(stringResource(Res.string.connect_wallet_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryLoading() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(NwcWalletConnectionTestTags.DIALOG_LOADING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Text(stringResource(Res.string.connect_wallet_loading))
    }
}

@Composable
private fun DiscoveryDetails(state: ConnectNwcWalletUiState, onAliasChange: (String) -> Unit) {
    val discovery = state.discovery ?: return
    val focusManager = LocalFocusManager.current
    val finishAliasEditing = { focusManager.clearFocus(force = true) }
    val doneLabel = stringResource(Res.string.keyboard_done)

    Column(
        modifier = Modifier.testTag(NwcWalletConnectionTestTags.DIALOG_DETAILS),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = state.alias,
            onValueChange = onAliasChange,
            label = { Text(stringResource(Res.string.connect_wallet_alias_label)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(NwcWalletConnectionTestTags.DIALOG_ALIAS_FIELD),
            enabled = !state.isSaving,
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    imeAction = ImeAction.Done,
                    platformImeOptions =
                        doneKeyboardPlatformImeOptions(
                            doneLabel = doneLabel,
                            onDone = finishAliasEditing
                        )
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = { finishAliasEditing() }
                )
        )

        WalletSummary(discovery)
        CapabilitySection(
            title = stringResource(Res.string.connect_wallet_details_methods),
            values = discovery.metadata.methods
        )
        CapabilitySection(
            title = stringResource(Res.string.connect_wallet_details_encryption),
            values = discovery.metadata.encryptionSchemes
        )
        discovery.metadata.negotiatedEncryption?.let { scheme ->
            Text(
                text =
                    stringResource(
                        Res.string.connect_wallet_details_encryption_active,
                        formatEncryptionScheme(scheme)
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WalletSummary(discovery: NwcWalletDiscovery) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(Res.string.connect_wallet_details_pubkey),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = ellipsize(discovery.walletPublicKey),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        discovery.relayUrl?.let { relay ->
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.connect_wallet_details_relay),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = relay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        discovery.lightningAddress?.let { address ->
            Spacer(Modifier.size(4.dp))
            Text(
                text = stringResource(Res.string.connect_wallet_details_lud16),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = address,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CapabilitySection(title: String, values: Set<String>) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = values.sorted().joinToString(separator = ", "),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WarningSection(discovery: NwcWalletDiscovery) {
    val warnings =
        buildList {
            if (!discovery.supportsPayInvoice) {
                add(
                    stringResource(
                        Res.string.connect_wallet_warning_missing_pay_invoice
                    )
                )
            }
            when {
                discovery.usesLegacyEncryption &&
                    discovery.metadata.encryptionDefaultedToNip04 ->
                    add(
                        stringResource(
                            Res.string.connect_wallet_warning_legacy_nip04_default
                        )
                    )

                discovery.usesLegacyEncryption ->
                    add(
                        stringResource(
                            Res.string.connect_wallet_warning_legacy_nip04
                        )
                    )

                !discovery.supportsNip44 ->
                    add(
                        stringResource(
                            Res.string.connect_wallet_warning_missing_nip44
                        )
                    )
            }
        }
    if (warnings.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(NwcWalletConnectionTestTags.DIALOG_WARNING)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(Res.string.connect_wallet_warning_heading),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.labelMedium
            )
            warnings.forEach { warning ->
                Text(
                    text = warning,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun ellipsize(value: String): String = if (value.length <= 24) {
    value
} else {
    value.take(12) + "…" + value.takeLast(6)
}

private fun formatEncryptionScheme(value: String): String = when (value.lowercase()) {
    "nip44_v2" -> "NIP-44 v2"
    "nip04" -> "NIP-04"
    else -> value
}
