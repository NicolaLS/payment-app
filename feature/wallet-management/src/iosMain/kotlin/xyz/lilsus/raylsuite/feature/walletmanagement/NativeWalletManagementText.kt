package xyz.lilsus.raylsuite.feature.walletmanagement

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

data class NativeWalletManagementText(
    val settingsTitle: String,
    val disconnectedSubtitle: String,
    val screenTitle: String,
    val emptyDescription: String,
    val addTitle: String,
    val removeTitle: String,
    val removeConfirmationTitle: String,
    val removeConfirmationBody: String,
    val cancelTitle: String
)

suspend fun nativeWalletManagementText(): NativeWalletManagementText = NativeWalletManagementText(
    settingsTitle = nativeString(
        NativeStringResource(table = "WalletManagement", key = "settings_manage_wallet")
    ),
    disconnectedSubtitle = nativeString(
        NativeStringResource(table = "WalletManagement", key = "settings_manage_wallet_subtitle")
    ),
    screenTitle = nativeString(
        NativeStringResource(table = "WalletManagement", key = "settings_manage_wallet_title")
    ),
    emptyDescription = nativeString(
        NativeStringResource(table = "WalletManagement", key = "settings_manage_wallet_placeholder")
    ),
    addTitle = nativeString(
        NativeStringResource(table = "WalletManagement", key = "settings_manage_wallet_add")
    ),
    removeTitle = nativeString(
        NativeStringResource(table = "WalletManagement", key = "settings_manage_wallet_remove")
    ),
    removeConfirmationTitle =
        nativeString(
            NativeStringResource(
                table = "WalletManagement",
                key = "settings_manage_wallet_remove_confirmation_title"
            )
        ),
    removeConfirmationBody =
        nativeString(
            NativeStringResource(
                table = "WalletManagement",
                key = "settings_manage_wallet_remove_confirmation_body"
            )
        ),
    cancelTitle = nativeString(
        NativeStringResource(table = "WalletManagement", key = "settings_manage_wallet_cancel")
    )
)
