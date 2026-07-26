package xyz.lilsus.lasr.feature.walletconnection

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
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.Res
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_description
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_scan_instruction
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_scan_permission
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_title
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_uri_label
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.add_wallet_uri_placeholder
import xyz.lilsus.lasr.ui.lasrConnectionErrorMessageFor
import xyz.lilsus.raylsuite.core.camera.CameraPreviewHost
import xyz.lilsus.raylsuite.core.camera.QrScannerController
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNwcWalletScreen(
    state: AddNwcWalletUiState,
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
        modifier = modifier.testTag(NwcWalletConnectionTestTags.SCREEN),
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
            modifier =
                Modifier
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(NwcWalletConnectionTestTags.URI_FIELD),
                singleLine = true,
                label = { Text(stringResource(Res.string.add_wallet_uri_label)) },
                placeholder = {
                    Text(stringResource(Res.string.add_wallet_uri_placeholder))
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = { submitOrClearFocus() }
                    )
            )
            state.error?.let { error ->
                Text(
                    text = lasrConnectionErrorMessageFor(error),
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
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(NwcWalletConnectionTestTags.CAMERA_CARD)
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.3f)
                        .testTag(NwcWalletConnectionTestTags.CAMERA_PREVIEW)
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

object NwcWalletConnectionTestTags {
    const val SCREEN = "nwc_wallet_screen"
    const val URI_FIELD = "nwc_wallet_uri_field"
    const val CAMERA_CARD = "nwc_wallet_camera_card"
    const val CAMERA_PREVIEW = "nwc_wallet_camera_preview"
    const val CONFIRM_DIALOG = "nwc_wallet_confirm_dialog"
    const val DIALOG_LOADING = "nwc_wallet_dialog_loading"
    const val DIALOG_DETAILS = "nwc_wallet_dialog_details"
    const val DIALOG_ALIAS_FIELD = "nwc_wallet_dialog_alias_field"
    const val DIALOG_WARNING = "nwc_wallet_dialog_warning"
    const val DIALOG_CONFIRM_BUTTON = "nwc_wallet_dialog_confirm_button"
    const val DIALOG_CANCEL_BUTTON = "nwc_wallet_dialog_cancel_button"
    const val DIALOG_RETRY_BUTTON = "nwc_wallet_dialog_retry_button"
}
