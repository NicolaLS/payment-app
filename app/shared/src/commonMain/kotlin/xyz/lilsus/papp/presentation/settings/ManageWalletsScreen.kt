package xyz.lilsus.papp.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lasr.shared.generated.resources.Res
import lasr.shared.generated.resources.settings_manage_wallets_active
import lasr.shared.generated.resources.settings_manage_wallets_add
import lasr.shared.generated.resources.settings_manage_wallets_placeholder
import lasr.shared.generated.resources.settings_manage_wallets_remove
import lasr.shared.generated.resources.settings_manage_wallets_set_active
import lasr.shared.generated.resources.settings_manage_wallets_title
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.presentation.common.BackIconButton
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
    onWalletDetails: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(MaestroTags.Settings.MANAGE_WALLETS_SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_manage_wallets_title)) },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
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
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (state.hasWallets) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MaestroTags.Settings.MANAGE_WALLETS_ADD_BUTTON),
                    onClick = onAddWallet
                ) {
                    Text(text = stringResource(Res.string.settings_manage_wallets_add))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    state.wallets.forEach { row ->
                        WalletCard(
                            wallet = row.wallet,
                            isActive = row.isActive,
                            onDetails = { onWalletDetails(row.wallet.pubKey) },
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
    onDetails: () -> Unit,
    onRemoveWallet: () -> Unit,
    onSetActive: () -> Unit
) {
    Surface(
        modifier = Modifier
            .heightIn(48.dp)
            .fillMaxWidth()
            .testTag(MaestroTags.Settings.walletRow(wallet.testLabel()))
            .clickable(onClick = onDetails),
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
                        modifier = Modifier.testTag(
                            MaestroTags.Settings.walletActiveBadge(wallet.testLabel())
                        ),
                        text = stringResource(Res.string.settings_manage_wallets_active),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // Show relay/lud16 only for NWC wallets
            if (wallet.type == WalletType.NWC) {
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
            }
            HorizontalDivider()
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag(
                            MaestroTags.Settings.walletRemoveButton(wallet.testLabel())
                        ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = onRemoveWallet
                ) {
                    Text(
                        text = stringResource(Res.string.settings_manage_wallets_remove),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag(
                            MaestroTags.Settings.walletSetActiveButton(wallet.testLabel())
                        ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    onClick = onSetActive,
                    enabled = !isActive
                ) {
                    Text(
                        text = stringResource(Res.string.settings_manage_wallets_set_active),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWalletState(onAddWallet: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MaestroTags.Settings.MANAGE_WALLETS_EMPTY),
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
            Button(
                modifier = Modifier.testTag(MaestroTags.Settings.MANAGE_WALLETS_ADD_BUTTON),
                onClick = onAddWallet
            ) {
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
            onRemoveWallet = {},
            onWalletDetails = {}
        )
    }
}

private fun abbreviateKey(value: String): String = if (value.length <=
    16
) {
    value
} else {
    value.take(8) + "…" + value.takeLast(4)
}

private fun WalletDisplay.testLabel(): String = alias?.takeIf { it.isNotBlank() } ?: pubKey
