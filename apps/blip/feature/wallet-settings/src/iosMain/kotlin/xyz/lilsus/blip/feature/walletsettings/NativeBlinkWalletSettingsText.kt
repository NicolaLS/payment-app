package xyz.lilsus.blip.feature.walletsettings

import xyz.lilsus.blip.ui.nativeBlinkErrorMessageFor
import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

data class NativeBlinkWalletSettingsText(
    val refreshTitle: String,
    val statusMessage: String?,
    val statusIsError: Boolean,
    val removeTitle: String,
    val removeDialogTitle: String,
    val removeDialogDescription: String,
    val removeConfirmTitle: String,
    val removeCancelTitle: String
)

/** Resolves Blip-owned wallet Settings presentation without leaking generated resources. */
suspend fun nativeBlinkWalletSettingsText(
    state: BlinkWalletSettingsUiState
): NativeBlinkWalletSettingsText {
    val errorMessage = state.error?.let { nativeBlinkErrorMessageFor(it) }
    val statusMessage =
        errorMessage ?: if (state.refreshSucceeded) {
            nativeString(
                NativeStringResource(
                    table = "BlipWalletSettings",
                    key = "settings_blink_connection_refresh_success"
                )
            )
        } else {
            null
        }
    return NativeBlinkWalletSettingsText(
        refreshTitle = nativeString(
            NativeStringResource(
                table = "BlipWalletSettings",
                key = "settings_blink_connection_refresh"
            )
        ),
        statusMessage = statusMessage,
        statusIsError = errorMessage != null,
        removeTitle = nativeString(
            NativeStringResource(
                table = "BlipWalletSettings",
                key = "settings_blink_connection_remove"
            )
        ),
        removeDialogTitle = nativeString(
            NativeStringResource(
                table = "BlipWalletSettings",
                key = "settings_blink_connection_remove_title"
            )
        ),
        removeDialogDescription =
            nativeString(
                NativeStringResource(
                    table = "BlipWalletSettings",
                    key = "settings_blink_connection_remove_description"
                )
            ),
        removeConfirmTitle = nativeString(
            NativeStringResource(
                table = "BlipWalletSettings",
                key = "settings_blink_connection_remove_confirm"
            )
        ),
        removeCancelTitle = nativeString(
            NativeStringResource(
                table = "BlipWalletSettings",
                key = "settings_blink_connection_remove_cancel"
            )
        )
    )
}
