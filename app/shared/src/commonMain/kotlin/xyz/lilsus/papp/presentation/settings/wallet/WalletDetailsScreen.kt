package xyz.lilsus.papp.presentation.settings.wallet

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import lasr.shared.generated.resources.Res
import lasr.shared.generated.resources.settings_wallet_details_blink_default_id
import lasr.shared.generated.resources.settings_wallet_details_blink_default_id_hint
import lasr.shared.generated.resources.settings_wallet_details_blink_refresh
import lasr.shared.generated.resources.settings_wallet_details_connection_id
import lasr.shared.generated.resources.settings_wallet_details_default_id_unset
import lasr.shared.generated.resources.settings_wallet_details_import_contacts
import lasr.shared.generated.resources.settings_wallet_details_import_contacts_hint
import lasr.shared.generated.resources.settings_wallet_details_import_contacts_title
import lasr.shared.generated.resources.settings_wallet_details_title
import lasr.shared.generated.resources.settings_wallet_details_type
import lasr.shared.generated.resources.wallet_type_blink
import lasr.shared.generated.resources.wallet_type_nwc
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.presentation.common.BackIconButton
import xyz.lilsus.papp.presentation.common.errorMessageFor
import xyz.lilsus.papp.presentation.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletDetailsScreen(
    state: WalletDetailsUiState,
    onBack: () -> Unit,
    onRefreshBlinkDefaultWallet: () -> Unit,
    onImportBlinkContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val errorText = state.error?.let { errorMessageFor(it) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(
                            Res.string.settings_wallet_details_title
                        )
                    )
                },
                navigationIcon = {
                    BackIconButton(onClick = onBack)
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = state.alias?.takeIf { it.isNotBlank() } ?: state.walletId,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DetailRow(
                        label = stringResource(Res.string.settings_wallet_details_type),
                        value = walletTypeLabel(state.walletType)
                    )
                    HorizontalDivider()
                    DetailRow(
                        label = stringResource(
                            Res.string.settings_wallet_details_connection_id
                        ),
                        value = state.walletId
                    )
                }
            }

            if (state.isBlink) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 4.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DetailRow(
                            label = stringResource(
                                Res.string.settings_wallet_details_blink_default_id
                            ),
                            value = state.blinkDefaultWalletId
                                ?: stringResource(
                                    Res.string.settings_wallet_details_default_id_unset
                                )
                        )
                        Text(
                            text = stringResource(
                                Res.string.settings_wallet_details_blink_default_id_hint
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onRefreshBlinkDefaultWallet,
                            enabled = !state.isRefreshing && !state.isMissing
                        ) {
                            Text(
                                text = stringResource(
                                    Res.string.settings_wallet_details_blink_refresh
                                )
                            )
                        }
                        errorText?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                BlinkContactImportCard(
                    onImport = onImportBlinkContacts
                )
            }

            if (!state.isBlink) {
                errorText?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun BlinkContactImportCard(onImport: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings_wallet_details_import_contacts_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.settings_wallet_details_import_contacts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        Res.string.settings_wallet_details_import_contacts
                    )
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun walletTypeLabel(type: WalletType): String = when (type) {
    WalletType.NWC -> stringResource(Res.string.wallet_type_nwc)
    WalletType.BLINK -> stringResource(Res.string.wallet_type_blink)
}

@Preview
@Composable
private fun WalletDetailsScreenPreview() {
    AppTheme {
        WalletDetailsScreen(
            state = WalletDetailsUiState(
                walletId = "blink-123",
                alias = "Blink Wallet",
                walletType = WalletType.BLINK,
                blinkDefaultWalletId = "wallet-987"
            ),
            onBack = {},
            onRefreshBlinkDefaultWallet = {},
            onImportBlinkContacts = {}
        )
    }
}
