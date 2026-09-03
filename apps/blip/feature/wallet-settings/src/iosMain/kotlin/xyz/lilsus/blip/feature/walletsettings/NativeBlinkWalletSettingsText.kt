package xyz.lilsus.blip.feature.walletsettings

import xyz.lilsus.blip.ui.nativeBlinkErrorMessageFor
import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

data class NativeBlinkWalletSettingsText(
    val fundingWalletTitle: String,
    val fundingWalletPickerTitle: String,
    val chooseFundingWalletTitle: String,
    val bitcoinTitle: String,
    val stablesatsTitle: String,
    val loadingTitle: String,
    val unavailableMessage: String,
    val closeTitle: String,
    val errorMessage: String?,
    val removeTitle: String,
    val removeDialogTitle: String,
    val removeDialogDescription: String,
    val removeConfirmTitle: String,
    val removeCancelTitle: String
)

/** Resolves Blip-owned wallet Settings presentation without leaking generated resources. */
suspend fun nativeBlinkWalletSettingsText(
    state: BlinkWalletSettingsUiState
): NativeBlinkWalletSettingsText = NativeBlinkWalletSettingsText(
    fundingWalletTitle = nativeString(
        NativeStringResource(
            table = "BlipWalletSettings",
            key = "settings_blink_funding_wallet"
        )
    ),
    fundingWalletPickerTitle = nativeString(
        NativeStringResource(
            table = "BlipWalletSettings",
            key = "settings_blink_funding_wallet_title"
        )
    ),
    chooseFundingWalletTitle = nativeString(
        NativeStringResource(
            table = "BlipWalletSettings",
            key = "settings_blink_funding_wallet_choose"
        )
    ),
    bitcoinTitle = nativeString(
        NativeStringResource(
            table = "BlipWalletSettings",
            key = "settings_blink_funding_wallet_bitcoin"
        )
    ),
    stablesatsTitle = nativeString(
        NativeStringResource(
            table = "BlipWalletSettings",
            key = "settings_blink_funding_wallet_stablesats"
        )
    ),
    loadingTitle = nativeString(
        NativeStringResource(
            table = "BlipWalletSettings",
            key = "settings_blink_funding_wallet_loading"
        )
    ),
    unavailableMessage = nativeString(
        NativeStringResource(
            table = "BlipWalletSettings",
            key = "settings_blink_funding_wallet_unavailable"
        )
    ),
    closeTitle = nativeString(
        NativeStringResource(
            table = "BlipWalletSettings",
            key = "settings_blink_funding_wallet_close"
        )
    ),
    errorMessage = state.error?.let { nativeBlinkErrorMessageFor(it) },
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
