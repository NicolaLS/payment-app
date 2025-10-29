package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.settings_manage_wallets_placeholder
import papp.composeapp.generated.resources.settings_manage_wallets_add
import papp.composeapp.generated.resources.settings_manage_wallets_remove
import papp.composeapp.generated.resources.settings_manage_wallets_replace
import papp.composeapp.generated.resources.settings_manage_wallets_title
import xyz.lilsus.papp.presentation.settings.wallet.WalletDisplay
import xyz.lilsus.papp.presentation.settings.wallet.WalletSettingsUiState
import xyz.lilsus.papp.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWalletsScreen(
    state: WalletSettingsUiState,
    onBack: () -> Unit,
    onAddWallet: () -> Unit,
    onRemoveWallet: () -> Unit,
    modifier: Modifier = Modifier,
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
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            state.wallet?.let { wallet ->
                WalletCard(
                    wallet = wallet,
                    onRemoveWallet = onRemoveWallet,
                    onReplaceWallet = onAddWallet,
                )
            } ?: run {
                EmptyWalletState(onAddWallet = onAddWallet)
            }
        }
    }
}

@Composable
private fun WalletCard(
    wallet: WalletDisplay,
    onRemoveWallet: () -> Unit,
    onReplaceWallet: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = abbreviateKey(wallet.pubKey),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            wallet.relay?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            wallet.lud16?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Divider()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(onClick = onRemoveWallet) {
                    Text(text = stringResource(Res.string.settings_manage_wallets_remove))
                }
                Button(onClick = onReplaceWallet) {
                    Text(text = stringResource(Res.string.settings_manage_wallets_replace))
                }
            }
        }
    }
}

@Composable
private fun EmptyWalletState(onAddWallet: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_manage_wallets_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
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
                wallet = WalletDisplay(
                    pubKey = "npub1exampleexampleexampleexampleexample",
                    relay = "wss://relay.example.com",
                    lud16 = "user@example.com",
                )
            ),
            onBack = {},
            onAddWallet = {},
            onRemoveWallet = {},
        )
    }
}

private fun abbreviateKey(value: String): String {
    return if (value.length <= 16) value else value.take(8) + "…" + value.takeLast(4)
}
