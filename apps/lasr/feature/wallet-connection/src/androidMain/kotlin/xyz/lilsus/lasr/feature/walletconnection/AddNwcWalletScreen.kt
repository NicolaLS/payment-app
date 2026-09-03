package xyz.lilsus.lasr.feature.walletconnection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import xyz.lilsus.lasr.feature.walletconnection.R
import xyz.lilsus.raylsuite.core.camera.CameraAuthorizationState
import xyz.lilsus.raylsuite.core.ui.components.BackIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNwcWalletScreen(
    state: AddNwcWalletUiState,
    onBack: () -> Unit,
    onUriChange: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
    onQrCodeScanned: (String) -> Unit,
    onCameraPermissionAction: () -> Unit,
    cameraAuthorization: CameraAuthorizationState,
    canRequestCameraPermission: Boolean,
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
                title = { Text(stringResource(R.string.add_wallet_title)) },
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
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.add_wallet_description),
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
                label = { Text(stringResource(R.string.add_wallet_uri_label)) },
                placeholder = {
                    Text(stringResource(R.string.add_wallet_uri_placeholder))
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = { submitOrClearFocus() }
                    )
            )
            TextButton(
                onClick = onPaste,
                modifier = Modifier.testTag(NwcWalletConnectionTestTags.PASTE_BUTTON)
            ) {
                Text(stringResource(R.string.add_wallet_uri_paste))
            }
            state.error?.let { error ->
                Text(
                    text = lasrConnectionErrorMessageFor(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            CameraCard(
                authorization = cameraAuthorization,
                canRequestPermission = canRequestCameraPermission,
                onQrCodeScanned = onQrCodeScanned,
                onCameraPermissionAction = onCameraPermissionAction
            )
        }
    }
}

@Composable
private fun CameraCard(
    authorization: CameraAuthorizationState,
    canRequestPermission: Boolean,
    onQrCodeScanned: (String) -> Unit,
    onCameraPermissionAction: () -> Unit
) {
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
                text = stringResource(R.string.add_wallet_scan_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (authorization == CameraAuthorizationState.AUTHORIZED) {
                NwcConnectionQrScannerPreview(
                    onQrCodeScanned = onQrCodeScanned,
                    onCameraPermissionMissing = onCameraPermissionAction,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.3f)
                            .testTag(NwcWalletConnectionTestTags.CAMERA_PREVIEW)
                )
            }
            if (authorization != CameraAuthorizationState.AUTHORIZED) {
                Text(
                    text =
                        stringResource(
                            if (
                                authorization == CameraAuthorizationState.RESTRICTED ||
                                authorization == CameraAuthorizationState.UNAVAILABLE
                            ) {
                                R.string.add_wallet_scan_restricted
                            } else {
                                R.string.add_wallet_scan_permission
                            }
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                if (
                    authorization == CameraAuthorizationState.NOT_DETERMINED ||
                    authorization == CameraAuthorizationState.DENIED
                ) {
                    Button(onClick = onCameraPermissionAction) {
                        Text(
                            stringResource(
                                if (
                                    authorization == CameraAuthorizationState.DENIED &&
                                    !canRequestPermission
                                ) {
                                    R.string.add_wallet_scan_open_settings
                                } else if (authorization == CameraAuthorizationState.DENIED) {
                                    R.string.add_wallet_scan_retry
                                } else {
                                    R.string.add_wallet_scan_allow_camera
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

object NwcWalletConnectionTestTags {
    const val SCREEN = "nwc_wallet_screen"
    const val URI_FIELD = "nwc_wallet_uri_field"
    const val PASTE_BUTTON = "nwc_wallet_paste_button"
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
