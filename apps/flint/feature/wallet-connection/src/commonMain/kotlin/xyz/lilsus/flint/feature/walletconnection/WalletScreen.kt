package xyz.lilsus.flint.feature.walletconnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.flint.application.wallet.CredentialProblemKind
import xyz.lilsus.flint.application.wallet.WalletAccessState
import xyz.lilsus.flint.feature.walletconnection.generated.resources.Res
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_cancel
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_credential_corrupt
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_credential_invalidated
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_credential_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_credential_unavailable
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_already_configured
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_connection
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_invalid_mnemonic
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_reset
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_error_storage
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_import_action
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_import_body
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_import_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_phrase_hint
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_phrase_label
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_progress_connecting
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_progress_loading
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_progress_removing
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_progress_working
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reconnect_body
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reconnect_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_remove_body
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_remove_confirm
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_remove_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reset_action
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reset_body
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_reset_title
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_retry
import xyz.lilsus.flint.feature.walletconnection.generated.resources.wallet_storage_note
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletConnectionScreen(
    state: WalletUiState,
    onBack: (() -> Unit)?,
    dispatch: (WalletAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.wallet_import_title)) },
                navigationIcon = {
                    onBack?.let { BackIconButton(onClick = it) }
                }
            )
        }
    ) { padding ->
        WalletConnectionContent(
            state = state,
            dispatch = dispatch,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun WalletConnectionContent(
    state: WalletUiState,
    dispatch: (WalletAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (val access = state.access) {
            WalletAccessState.Loading,
            WalletAccessState.Connecting,
            WalletAccessState.Removing -> ProgressContent(access)

            WalletAccessState.NoWallet -> ImportContent(state, dispatch)

            WalletAccessState.ReconnectRequired -> RecoveryContent(
                title = Res.string.wallet_reconnect_title,
                body = Res.string.wallet_reconnect_body,
                dispatch = dispatch
            )

            WalletAccessState.ResetRequired -> RecoveryContent(
                title = Res.string.wallet_reset_title,
                body = Res.string.wallet_reset_body,
                dispatch = dispatch
            )

            is WalletAccessState.CredentialProblem -> CredentialProblemContent(
                kind = access.kind,
                dispatch = dispatch
            )

            WalletAccessState.Connected -> Unit
        }
    }
    if (state.confirmRemoval) {
        RemovalConfirmation(dispatch)
    }
}

@Composable
private fun ImportContent(state: WalletUiState, dispatch: (WalletAction) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(Res.string.wallet_import_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(Res.string.wallet_import_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.recoveryPhrase,
            onValueChange = { dispatch(WalletAction.RecoveryPhraseChanged(it)) },
            label = { Text(stringResource(Res.string.wallet_phrase_label)) },
            placeholder = { Text(stringResource(Res.string.wallet_phrase_hint)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password
                ),
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(Res.string.wallet_storage_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.message?.let { message ->
            Text(
                text = stringResource(message.resource()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
        Button(
            enabled = state.recoveryPhrase.isNotBlank(),
            onClick = { dispatch(WalletAction.Import) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.wallet_import_action))
        }
    }
}

@Composable
private fun ProgressContent(state: WalletAccessState) {
    val resource =
        when (state) {
            WalletAccessState.Loading -> Res.string.wallet_progress_loading
            WalletAccessState.Connecting -> Res.string.wallet_progress_connecting
            WalletAccessState.Removing -> Res.string.wallet_progress_removing
            else -> Res.string.wallet_progress_working
        }
    CircularProgressIndicator()
    Text(
        text = stringResource(resource),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 20.dp)
    )
}

@Composable
private fun CredentialProblemContent(
    kind: CredentialProblemKind,
    dispatch: (WalletAction) -> Unit
) {
    val body =
        when (kind) {
            CredentialProblemKind.UNAVAILABLE -> Res.string.wallet_credential_unavailable
            CredentialProblemKind.INVALIDATED -> Res.string.wallet_credential_invalidated
            CredentialProblemKind.CORRUPT -> Res.string.wallet_credential_corrupt
        }
    RecoveryContent(Res.string.wallet_credential_title, body, dispatch)
}

@Composable
private fun RecoveryContent(
    title: StringResource,
    body: StringResource,
    dispatch: (WalletAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = { dispatch(WalletAction.Retry) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.wallet_retry))
        }
        OutlinedButton(
            onClick = { dispatch(WalletAction.RequestRemoval) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.wallet_reset_action))
        }
    }
}

@Composable
private fun RemovalConfirmation(dispatch: (WalletAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { dispatch(WalletAction.CancelRemoval) },
        title = { Text(stringResource(Res.string.wallet_remove_title)) },
        text = { Text(stringResource(Res.string.wallet_remove_body)) },
        confirmButton = {
            TextButton(onClick = { dispatch(WalletAction.ConfirmRemoval) }) {
                Text(stringResource(Res.string.wallet_remove_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { dispatch(WalletAction.CancelRemoval) }) {
                Text(stringResource(Res.string.wallet_cancel))
            }
        }
    )
}

private fun WalletMessage.resource(): StringResource = when (this) {
    WalletMessage.ALREADY_CONFIGURED -> Res.string.wallet_error_already_configured
    WalletMessage.INVALID_MNEMONIC -> Res.string.wallet_error_invalid_mnemonic
    WalletMessage.CONNECTION_FAILED -> Res.string.wallet_error_connection
    WalletMessage.CREDENTIAL_STORE_FAILED -> Res.string.wallet_error_storage
    WalletMessage.RESET_REQUIRED -> Res.string.wallet_error_reset
}
