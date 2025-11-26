package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.*
import xyz.lilsus.papp.presentation.settings.wallet.WalletDisplay
import xyz.lilsus.papp.presentation.settings.wallet.WalletRow
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsUiState
import xyz.lilsus.papp.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWalletsScreen(
    state: WalletSettingsUiState,
    onBack: () -> Unit,
    onAddWallet: () -> Unit,
    onSelectWallet: (String) -> Unit,
    onRemoveWallet: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_manage_wallets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onAddWallet
            ) {
                Text(text = stringResource(Res.string.settings_manage_wallets_add))
            }
            if (state.hasWallets) {
                Column(
                    modifier = Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    state.wallets.forEach { row ->
                        WalletCard(
                            wallet = row.wallet,
                            isActive = row.isActive,
                            onRemoveWallet = { onRemoveWallet(row.wallet.pubKey) },
                            onSetActive = { onSelectWallet(row.wallet.pubKey) }
                        )
                    }
                }
            } else {
                EmptyWalletState(onAddWallet = onAddWallet)
            }
        }
    }
}

@Composable
private fun WalletCard(
    wallet: WalletDisplay,
    isActive: Boolean,
    onRemoveWallet: () -> Unit,
    onSetActive: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (isActive) 8.dp else 4.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    wallet.alias?.takeIf { it.isNotBlank() }?.let { alias ->
                        Text(
                            text = alias,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = abbreviateKey(wallet.pubKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } ?: Text(
                        text = abbreviateKey(wallet.pubKey),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isActive) {
                    Text(
                        text = stringResource(Res.string.settings_manage_wallets_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            wallet.relay?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            wallet.lud16?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onRemoveWallet) {
                    Text(text = stringResource(Res.string.settings_manage_wallets_remove))
                }
                Button(
                    onClick = onSetActive,
                    enabled = !isActive
                ) {
                    Text(text = stringResource(Res.string.settings_manage_wallets_set_active))
                }
            }
        }
    }
}

@Composable
private fun EmptyWalletState(onAddWallet: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings_manage_wallets_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onAddWallet) {
                Text(text = stringResource(Res.string.settings_manage_wallets_add))
            }
        }
    }
}

@Preview
@Composable
private fun ManageWalletsScreenPreview() {
    AppTheme {
        ManageWalletsScreen(
            state = WalletSettingsUiState(
                wallets = listOf(
                    WalletRow(
                        wallet = WalletDisplay(
                            pubKey = "npub1exampleexampleexampleexampleexample",
                            relay = "wss://relay.example.com",
                            lud16 = "user@example.com",
                            alias = "Primary Wallet"
                        ),
                        isActive = true
                    ),
                    WalletRow(
                        wallet = WalletDisplay(
                            pubKey = "npub1anotherexampleexampleexample",
                            relay = "wss://relay.example2.com",
                            lud16 = null,
                            alias = null
                        ),
                        isActive = false
                    )
                )
            ),
            onBack = {},
            onAddWallet = {},
            onSelectWallet = {},
            onRemoveWallet = {}
        )
    }
}

private fun abbreviateKey(value: String): String = if (value.length <= 16) value else value.take(8) + "…" + value.takeLast(4)
