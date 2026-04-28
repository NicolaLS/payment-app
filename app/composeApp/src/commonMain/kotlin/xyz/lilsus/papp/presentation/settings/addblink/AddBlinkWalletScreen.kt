package xyz.lilsus.papp.presentation.settings.addblink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.add_blink_wallet_alias_label
import lasr.composeapp.generated.resources.add_blink_wallet_alias_placeholder
import lasr.composeapp.generated.resources.add_blink_wallet_api_key_label
import lasr.composeapp.generated.resources.add_blink_wallet_api_key_placeholder
import lasr.composeapp.generated.resources.add_blink_wallet_connect
import lasr.composeapp.generated.resources.add_blink_wallet_description
import lasr.composeapp.generated.resources.add_blink_wallet_title
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.presentation.common.errorMessageFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlinkWalletScreen(
    state: AddBlinkWalletUiState,
    onBack: () -> Unit,
    onAliasChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val focusManager = LocalFocusManager.current
    val apiKeyFocusRequester = remember { FocusRequester() }
    var apiKeyVisible by remember { mutableStateOf(false) }
    val submitOrClearFocus = {
        focusManager.clearFocus(force = true)
        if (state.canSubmit) {
            onSubmit()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.add_blink_wallet_title)) },
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
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag(MaestroTags.BlinkWallet.SCREEN),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(Res.string.add_blink_wallet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.alias,
                onValueChange = onAliasChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaestroTags.BlinkWallet.ALIAS_FIELD),
                singleLine = true,
                label = { Text(stringResource(Res.string.add_blink_wallet_alias_label)) },
                placeholder = {
                    Text(stringResource(Res.string.add_blink_wallet_alias_placeholder))
                },
                enabled = !state.isSaving,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { apiKeyFocusRequester.requestFocus() }
                )
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(apiKeyFocusRequester)
                    .testTag(MaestroTags.BlinkWallet.API_KEY_FIELD),
                singleLine = true,
                label = { Text(stringResource(Res.string.add_blink_wallet_api_key_label)) },
                placeholder = {
                    Text(stringResource(Res.string.add_blink_wallet_api_key_placeholder))
                },
                enabled = !state.isSaving,
                visualTransformation = if (apiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = { submitOrClearFocus() },
                    onDone = { submitOrClearFocus() }
                ),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = null
                        )
                    }
                }
            )

            if (state.error != null) {
                Text(
                    text = errorMessageFor(state.error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.weight(1f, fill = true))

            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaestroTags.BlinkWallet.CONNECT_BUTTON)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(text = stringResource(Res.string.add_blink_wallet_connect))
            }
        }
    }
}
