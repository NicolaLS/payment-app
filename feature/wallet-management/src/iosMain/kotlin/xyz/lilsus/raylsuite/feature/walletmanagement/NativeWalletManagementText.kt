package xyz.lilsus.raylsuite.feature.walletmanagement

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.Res
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_add
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_cancel
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_placeholder
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_remove
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_remove_confirmation_body
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_remove_confirmation_title
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_subtitle
import xyz.lilsus.raylsuite.feature.walletmanagement.generated.resources.settings_manage_wallet_title

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
    settingsTitle = getString(Res.string.settings_manage_wallet),
    disconnectedSubtitle = getString(Res.string.settings_manage_wallet_subtitle),
    screenTitle = getString(Res.string.settings_manage_wallet_title),
    emptyDescription = getString(Res.string.settings_manage_wallet_placeholder),
    addTitle = getString(Res.string.settings_manage_wallet_add),
    removeTitle = getString(Res.string.settings_manage_wallet_remove),
    removeConfirmationTitle =
        getString(Res.string.settings_manage_wallet_remove_confirmation_title),
    removeConfirmationBody =
        getString(Res.string.settings_manage_wallet_remove_confirmation_body),
    cancelTitle = getString(Res.string.settings_manage_wallet_cancel)
)
