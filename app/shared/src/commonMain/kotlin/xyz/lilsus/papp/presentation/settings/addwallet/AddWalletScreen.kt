package xyz.lilsus.papp.presentation.settings.addwallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import lasr.shared.generated.resources.Res
import lasr.shared.generated.resources.add_wallet_description
import lasr.shared.generated.resources.add_wallet_scan_instruction
import lasr.shared.generated.resources.add_wallet_scan_permission
import lasr.shared.generated.resources.add_wallet_title
import lasr.shared.generated.resources.add_wallet_uri_label
import lasr.shared.generated.resources.add_wallet_uri_placeholder
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.presentation.common.BackIconButton
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
    val focusManager = LocalFocusManager.current
    val submitOrClearFocus = {
        focusManager.clearFocus(force = true)
        onSubmit()
    }
    Scaffold(
        modifier = modifier.testTag(MaestroTags.NwcWallet.SCREEN),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.add_wallet_title)) },
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
                text = stringResource(Res.string.add_wallet_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.uri,
                onValueChange = onUriChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaestroTags.NwcWallet.URI_FIELD),
                singleLine = true,
                label = { Text(text = stringResource(Res.string.add_wallet_uri_label)) },
                placeholder = {
                    Text(text = stringResource(Res.string.add_wallet_uri_placeholder))
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { submitOrClearFocus() }
                )
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
        }
    }
}

@Composable
private fun CameraCard(controller: QrScannerController, hasPermission: Boolean) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MaestroTags.NwcWallet.CAMERA_CARD)
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
                    .testTag(MaestroTags.NwcWallet.CAMERA_PREVIEW)
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
