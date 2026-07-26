package xyz.lilsus.blip.feature.walletdetails

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.walletdetails.generated.resources.Res
import xyz.lilsus.blip.feature.walletdetails.generated.resources.settings_wallet_details_blink_default_id
import xyz.lilsus.blip.feature.walletdetails.generated.resources.settings_wallet_details_blink_default_id_hint
import xyz.lilsus.blip.feature.walletdetails.generated.resources.settings_wallet_details_blink_refresh
import xyz.lilsus.blip.feature.walletdetails.generated.resources.settings_wallet_details_default_id_unset
import xyz.lilsus.blip.feature.walletdetails.generated.resources.settings_wallet_details_title
import xyz.lilsus.blip.ui.blinkErrorMessageFor
import xyz.lilsus.blip.ui.generated.resources.Res as BlipUiRes
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_import
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_import_hint
import xyz.lilsus.blip.ui.generated.resources.blink_contacts_title
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlinkWalletDetailsScreen(
    state: BlinkWalletDetailsUiState,
    onBack: () -> Unit,
    onRefreshDefaultWallet: () -> Unit,
    onImportContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val errorText = state.error?.let { blinkErrorMessageFor(it) }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(Res.string.settings_wallet_details_title))
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
                text = state.alias,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            DefaultWalletCard(
                state = state,
                errorText = errorText,
                onRefresh = onRefreshDefaultWallet
            )

            BlinkContactImportCard(onImport = onImportContacts)
        }
    }
}

@Composable
private fun DefaultWalletCard(
    state: BlinkWalletDetailsUiState,
    errorText: String?,
    onRefresh: () -> Unit
) {
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
                label = stringResource(Res.string.settings_wallet_details_blink_default_id),
                value = state.defaultWalletId
                    ?: stringResource(Res.string.settings_wallet_details_default_id_unset)
            )
            Text(
                text = stringResource(
                    Res.string.settings_wallet_details_blink_default_id_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onRefresh,
                enabled = !state.isRefreshing && !state.isMissing
            ) {
                Text(stringResource(Res.string.settings_wallet_details_blink_refresh))
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
                text = stringResource(BlipUiRes.string.blink_contacts_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(BlipUiRes.string.blink_contacts_import_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onImport,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(BlipUiRes.string.blink_contacts_import))
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
