package xyz.lilsus.blip.feature.walletsettings

import org.jetbrains.compose.resources.getString
import xyz.lilsus.blip.feature.walletsettings.generated.resources.Res
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_refresh
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_refresh_success
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove_cancel
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove_confirm
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove_description
import xyz.lilsus.blip.feature.walletsettings.generated.resources.settings_blink_connection_remove_title
import xyz.lilsus.blip.ui.nativeBlinkErrorMessageFor

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
            getString(Res.string.settings_blink_connection_refresh_success)
        } else {
            null
        }
    return NativeBlinkWalletSettingsText(
        refreshTitle = getString(Res.string.settings_blink_connection_refresh),
        statusMessage = statusMessage,
        statusIsError = errorMessage != null,
        removeTitle = getString(Res.string.settings_blink_connection_remove),
        removeDialogTitle = getString(Res.string.settings_blink_connection_remove_title),
        removeDialogDescription =
            getString(Res.string.settings_blink_connection_remove_description),
        removeConfirmTitle = getString(Res.string.settings_blink_connection_remove_confirm),
        removeCancelTitle = getString(Res.string.settings_blink_connection_remove_cancel)
    )
}
