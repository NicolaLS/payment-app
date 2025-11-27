package xyz.lilsus.papp.presentation.settings.addwallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.add_wallet_continue
import papp.composeapp.generated.resources.add_wallet_description
import papp.composeapp.generated.resources.add_wallet_scan_instruction
import papp.composeapp.generated.resources.add_wallet_scan_permission
import papp.composeapp.generated.resources.add_wallet_title
import papp.composeapp.generated.resources.add_wallet_uri_label
import papp.composeapp.generated.resources.add_wallet_uri_placeholder
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
    modifier: Modifier = Modifier
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
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(Res.string.add_wallet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.uri,
                onValueChange = onUriChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(text = stringResource(Res.string.add_wallet_uri_label)) },
                placeholder = {
                    Text(text = stringResource(Res.string.add_wallet_uri_placeholder))
                }
            )
            if (state.error != null) {
                Text(
                    text = errorMessageFor(state.error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            CameraCard(
                controller = controller,
                hasPermission = isCameraPermissionGranted
            )
            Spacer(modifier = Modifier.weight(1f, fill = true))

            Button(
                onClick = onSubmit,
                enabled = state.canContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.add_wallet_continue))
            }
        }
    }
}

@Composable
private fun CameraCard(controller: QrScannerController, hasPermission: Boolean) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.add_wallet_scan_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CameraPreviewHost(
                controller = controller,
                visible = hasPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
            )
            if (!hasPermission) {
                Text(
                    text = stringResource(Res.string.add_wallet_scan_permission),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
