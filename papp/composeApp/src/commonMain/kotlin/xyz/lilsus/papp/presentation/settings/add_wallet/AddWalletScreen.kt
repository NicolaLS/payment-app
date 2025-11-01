package xyz.lilsus.papp.presentation.settings.add_wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.*
import xyz.lilsus.papp.presentation.common.errorMessageFor
import xyz.lilsus.papp.presentation.main.scan.CameraPreviewHost
import xyz.lilsus.papp.presentation.main.scan.QrScannerController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWalletScreen(
    state: AddWalletUiState,
    onBack: () -> Unit,
    onUriChange: (String) -> Unit,
    onSubmit: () -> Unit,
    controller: QrScannerController,
    isCameraPermissionGranted: Boolean,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.add_wallet_title)) },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(Res.string.add_wallet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.uri,
                onValueChange = onUriChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = stringResource(Res.string.add_wallet_uri_label)) },
                placeholder = { Text(text = stringResource(Res.string.add_wallet_uri_placeholder)) },
            )
            if (state.error != null) {
                Text(
                    text = errorMessageFor(state.error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            CameraCard(
                controller = controller,
                hasPermission = isCameraPermissionGranted,
            )
            Spacer(modifier = Modifier.weight(1f, fill = true))

            Button(
                onClick = onSubmit,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.add_wallet_continue))
            }
        }
    }
}

@Composable
private fun CameraCard(
    controller: QrScannerController,
    hasPermission: Boolean,
) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.add_wallet_scan_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CameraPreviewHost(
                controller = controller,
                visible = hasPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f),
            )
            if (!hasPermission) {
                Text(
                    text = stringResource(Res.string.add_wallet_scan_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
