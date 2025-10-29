package xyz.lilsus.papp.presentation.add_connection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatformTools
import xyz.lilsus.papp.presentation.common.errorMessageFor
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.connect_wallet_cancel
import papp.composeapp.generated.resources.connect_wallet_confirm
import papp.composeapp.generated.resources.connect_wallet_description
import papp.composeapp.generated.resources.connect_wallet_input_label
import papp.composeapp.generated.resources.connect_wallet_paste_placeholder
import papp.composeapp.generated.resources.connect_wallet_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectWalletDialog(
    initialUri: String? = null,
    onDismiss: () -> Unit,
) {
    val koin = remember { KoinPlatformTools.defaultContext().get() }
    val viewModel = remember { koin.get<ConnectWalletViewModel>() }

    DisposableEffect(viewModel) {
        onDispose { viewModel.clear() }
    }

    LaunchedEffect(initialUri) {
        if (!initialUri.isNullOrBlank()) {
            viewModel.updateUri(initialUri)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ConnectWalletEvent.Cancelled -> onDismiss()
                ConnectWalletEvent.Success -> onDismiss()
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(Res.string.connect_wallet_description),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = state.uri,
                    onValueChange = viewModel::updateUri,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.connect_wallet_input_label)) },
                    placeholder = { Text(stringResource(Res.string.connect_wallet_paste_placeholder)) },
                    enabled = !state.isSubmitting,
                )
                state.error?.let { error ->
                    Text(
                        text = errorMessageFor(error),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.submit() },
                enabled = state.uri.isNotBlank() && !state.isSubmitting,
            ) {
                if (state.isSubmitting) {
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

