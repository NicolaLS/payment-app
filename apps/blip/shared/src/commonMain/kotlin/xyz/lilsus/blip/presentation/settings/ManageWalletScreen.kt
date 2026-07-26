package xyz.lilsus.blip.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import org.jetbrains.compose.resources.stringResource
import rayl_suite.blip.shared.generated.resources.Res
import rayl_suite.blip.shared.generated.resources.settings_manage_wallet_add
import rayl_suite.blip.shared.generated.resources.settings_manage_wallet_placeholder
import rayl_suite.blip.shared.generated.resources.settings_manage_wallet_remove
import rayl_suite.blip.shared.generated.resources.settings_manage_wallet_title
import xyz.lilsus.blip.MaestroTags
import xyz.lilsus.blip.domain.model.WalletType
import xyz.lilsus.blip.presentation.common.BackIconButton
import xyz.lilsus.blip.presentation.settings.wallet.WalletDisplay
import xyz.lilsus.blip.presentation.settings.wallet.WalletSettingsUiState
import xyz.lilsus.blip.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWalletScreen(
    state: WalletSettingsUiState,
    onBack: () -> Unit,
    onAddWallet: () -> Unit,
    onRemoveWallet: () -> Unit,
    onWalletDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.testTag(MaestroTags.Settings.MANAGE_WALLET_SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_manage_wallet_title)) },
                navigationIcon = { BackIconButton(onClick = onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            state.wallet?.let { wallet ->
                WalletCard(
                    wallet = wallet,
                    onDetails = onWalletDetails,
                    onRemoveWallet = onRemoveWallet,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            } ?: EmptyWalletState(onAddWallet)
        }
    }
}

@Composable
private fun WalletCard(
    wallet: WalletDisplay,
    onDetails: () -> Unit,
    onRemoveWallet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MaestroTags.Settings.walletRow(wallet.testLabel()))
            .clickable(onClick = onDetails),
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = wallet.alias?.takeIf { it.isNotBlank() }
                    ?: wallet.type.displayName(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaestroTags.Settings.walletRemoveButton(wallet.testLabel())),
                onClick = onRemoveWallet
            ) {
                Text(stringResource(Res.string.settings_manage_wallet_remove))
            }
        }
    }
}

@Composable
private fun EmptyWalletState(onAddWallet: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MaestroTags.Settings.MANAGE_WALLET_EMPTY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.settings_manage_wallet_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
        Button(
            modifier = Modifier.testTag(MaestroTags.Settings.MANAGE_WALLET_ADD_BUTTON),
            onClick = onAddWallet
        ) {
            Text(stringResource(Res.string.settings_manage_wallet_add))
        }
    }
}

private fun WalletDisplay.testLabel(): String = alias?.takeIf { it.isNotBlank() } ?: connectionId

private fun WalletType.displayName(): String = when (this) {
    WalletType.NWC -> "NWC"
    WalletType.BLINK -> "Blink"
}

@Preview
@Composable
private fun ManageWalletScreenPreview() {
    AppTheme {
        ManageWalletScreen(
            state = WalletSettingsUiState(
                wallet = WalletDisplay(
                    connectionId = "nwc-wallet",
                    relay = "wss://relay.example.com",
                    lud16 = "user@example.com",
                    alias = "Wallet"
                )
            ),
            onBack = {},
            onAddWallet = {},
            onRemoveWallet = {},
            onWalletDetails = {}
        )
    }
}
