package xyz.lilsus.lasr.feature.walletdetails

import xyz.lilsus.raylsuite.core.ui.resources.NativeStringResource
import xyz.lilsus.raylsuite.core.ui.resources.nativeString

data class NativeNwcWalletDetailsText(
    val title: String,
    val typeLabel: String,
    val connectionIdLabel: String,
    val walletType: String
)

suspend fun nativeNwcWalletDetailsText(): NativeNwcWalletDetailsText = NativeNwcWalletDetailsText(
    title = nativeString(
        NativeStringResource(table = "LasrWalletDetails", key = "settings_wallet_details_title")
    ),
    typeLabel = nativeString(
        NativeStringResource(table = "LasrWalletDetails", key = "settings_wallet_details_type")
    ),
    connectionIdLabel = nativeString(
        NativeStringResource(
            table = "LasrWalletDetails",
            key = "settings_wallet_details_connection_id"
        )
    ),
    walletType = nativeString(
        NativeStringResource(table = "LasrWalletDetails", key = "wallet_type_nwc")
    )
)
