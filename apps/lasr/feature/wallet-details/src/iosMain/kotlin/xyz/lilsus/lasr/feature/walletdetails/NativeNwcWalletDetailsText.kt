package xyz.lilsus.lasr.feature.walletdetails

import org.jetbrains.compose.resources.getString
import xyz.lilsus.lasr.feature.walletdetails.generated.resources.Res
import xyz.lilsus.lasr.feature.walletdetails.generated.resources.settings_wallet_details_connection_id
import xyz.lilsus.lasr.feature.walletdetails.generated.resources.settings_wallet_details_title
import xyz.lilsus.lasr.feature.walletdetails.generated.resources.settings_wallet_details_type
import xyz.lilsus.lasr.feature.walletdetails.generated.resources.wallet_type_nwc

data class NativeNwcWalletDetailsText(
    val title: String,
    val typeLabel: String,
    val connectionIdLabel: String,
    val walletType: String
)

suspend fun nativeNwcWalletDetailsText(): NativeNwcWalletDetailsText = NativeNwcWalletDetailsText(
    title = getString(Res.string.settings_wallet_details_title),
    typeLabel = getString(Res.string.settings_wallet_details_type),
    connectionIdLabel = getString(Res.string.settings_wallet_details_connection_id),
    walletType = getString(Res.string.wallet_type_nwc)
)
