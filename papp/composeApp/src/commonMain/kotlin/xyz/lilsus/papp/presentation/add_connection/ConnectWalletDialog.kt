package xyz.lilsus.papp.presentation.add_connection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatformTools
import papp.composeapp.generated.resources.*
import xyz.lilsus.papp.domain.model.*
import xyz.lilsus.papp.presentation.common.errorMessageFor
import xyz.lilsus.papp.presentation.common.rememberRetainedInstance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectWalletDialog(initialUri: String? = null, onDismiss: () -> Unit) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = rememberRetainedInstance(
        factory = { koin.get<ConnectWalletViewModel>() },
        onDispose = { it.clear() }
    )

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            viewModel.load(initialUri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ConnectWalletEvent.Cancelled -> onDismiss()
                is ConnectWalletEvent.Success -> onDismiss()
            }
        }
    }

    val state by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = {
            viewModel.cancel()
        },
        title = { Text(text = stringResource(Res.string.connect_wallet_title)) },
        text = {
            ConnectWalletDialogContent(
                state = state,
                onAliasChange = viewModel::updateAlias,
                onSetActiveChange = viewModel::updateSetActive,
                onRetryDiscovery = viewModel::retryDiscovery
            )
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.confirm() },
                enabled = state.discovery != null && !state.isSaving && !state.isDiscoveryLoading
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(stringResource(Res.string.connect_wallet_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancel() }) {
                Text(stringResource(Res.string.connect_wallet_cancel))
            }
        }
    )
}

@Composable
private fun ConnectWalletDialogContent(
    state: ConnectWalletUiState,
    onAliasChange: (String) -> Unit,
    onSetActiveChange: (Boolean) -> Unit,
    onRetryDiscovery: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(Res.string.connect_wallet_description))
        state.discovery?.let { discovery ->
            WarningSection(discovery)
        }

        when {
            state.isDiscoveryLoading -> DiscoveryLoading()

            state.discovery != null -> DiscoveryDetails(
                state = state,
                onAliasChange = onAliasChange,
                onSetActiveChange = onSetActiveChange
            )

            else -> Unit
        }

        state.error?.let { error ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = errorMessageFor(error),
                    color = MaterialTheme.colorScheme.error
                )
                if (!state.isDiscoveryLoading && state.discovery == null) {
                    TextButton(onClick = onRetryDiscovery) {
                        Text(text = stringResource(Res.string.connect_wallet_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryLoading() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text = stringResource(Res.string.connect_wallet_loading))
    }
}

@Composable
private fun DiscoveryDetails(
    state: ConnectWalletUiState,
    onAliasChange: (String) -> Unit,
    onSetActiveChange: (Boolean) -> Unit
) {
    val discovery = state.discovery ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.aliasInput,
            onValueChange = onAliasChange,
            label = { Text(stringResource(Res.string.connect_wallet_alias_label)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = state.setActive,
                onCheckedChange = { onSetActiveChange(it) },
                enabled = !state.isSaving
            )
            Text(text = stringResource(Res.string.connect_wallet_set_active))
        }

        WalletSummary(discovery)

        CapabilitySection(
            title = stringResource(Res.string.connect_wallet_details_methods),
            values = discovery.methods
        )

        CapabilitySection(
            title = stringResource(Res.string.connect_wallet_details_encryption),
            values = discovery.encryptionSchemes
        )

        discovery.activeEncryption?.let { scheme ->
            Text(
                text = stringResource(
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
private fun WalletSummary(discovery: WalletDiscovery) {
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
        discovery.lud16?.let { address ->
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
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = values.sorted().joinToString(separator = ", "),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WarningSection(discovery: WalletDiscovery) {
    val warnings = buildList {
        if (!discovery.supportsPayInvoice) {
            add(stringResource(Res.string.connect_wallet_warning_missing_pay_invoice))
        }
        when {
            discovery.usesLegacyEncryption && discovery.encryptionDefaultedToNip04 ->
                add(stringResource(Res.string.connect_wallet_warning_legacy_nip04_default))

            discovery.usesLegacyEncryption ->
                add(stringResource(Res.string.connect_wallet_warning_legacy_nip04))

            !discovery.supportsNip44 ->
                add(stringResource(Res.string.connect_wallet_warning_missing_nip44))
        }
    }
    if (warnings.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
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

private fun ellipsize(value: String): String = if (value.length <= 24) value else value.take(12) + "…" + value.takeLast(6)

private fun formatEncryptionScheme(value: String): String = when (value.lowercase()) {
    "nip44_v2" -> "NIP-44 v2"
    "nip04" -> "NIP-04"
    else -> value
}
