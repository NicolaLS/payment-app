package xyz.lilsus.raylsuite.feature.walletmanagement

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

suspend fun nativeWalletRemovalMessage(isSubmitting: Boolean): String = nativeString(
    NativeStringResource(
        table = "WalletManagement",
        key = if (isSubmitting) {
            "wallet_removal_payment_active"
        } else {
            "wallet_removal_payment_warning"
        }
    )
)
