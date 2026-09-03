package xyz.lilsus.blip.feature.walletconnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.walletconnection.generated.resources.Res
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_api_key_label
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_api_key_placeholder
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_connect
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_description
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_hide_api_key
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_paste
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_show_api_key
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_title
import xyz.lilsus.blip.ui.blinkErrorMessageFor
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton
import xyz.lilsus.raylsuite.core.ui.platform.readPlainText

/** Android renderer for the Blip-owned Blink connection flow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBlinkWalletScreen(
    state: AddBlinkWalletUiState,
    onBack: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
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
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState())
                .testTag(BlinkWalletConnectionTestTags.SCREEN),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(Res.string.add_blink_wallet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column {
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(BlinkWalletConnectionTestTags.API_KEY_FIELD),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.add_blink_wallet_api_key_label)) },
                    placeholder = {
                        Text(stringResource(Res.string.add_blink_wallet_api_key_placeholder))
                    },
                    enabled = !state.isSaving,
                    visualTransformation =
                        if (apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onGo = { submitOrClearFocus() },
                            onDone = { submitOrClearFocus() }
                        ),
                    trailingIcon = {
                        val visibilityDescription =
                            stringResource(
                                if (apiKeyVisible) {
                                    Res.string.add_blink_wallet_hide_api_key
                                } else {
                                    Res.string.add_blink_wallet_show_api_key
                                }
                            )
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                imageVector =
                                    if (apiKeyVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                contentDescription = visibilityDescription
                            )
                        }
                    }
                )

                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            clipboard
                                .getClipEntry()
                                ?.readPlainText()
                                ?.trim()
                                ?.takeIf(String::isNotEmpty)
                                ?.let(onApiKeyChange)
                        }
                    },
                    enabled = !state.isSaving,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(Res.string.add_blink_wallet_paste))
                }
            }

            if (state.error != null) {
                Text(
                    text = blinkErrorMessageFor(state.error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BlinkWalletConnectionTestTags.CONNECT_BUTTON)
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

object BlinkWalletConnectionTestTags {
    const val SCREEN = "blink_wallet_screen"
    const val API_KEY_FIELD = "blink_wallet_api_key_field"
    const val CONNECT_BUTTON = "blink_wallet_connect_button"
}
