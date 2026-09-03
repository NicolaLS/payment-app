package xyz.lilsus.blip.feature.walletconnection

import org.jetbrains.compose.resources.getString
import xyz.lilsus.blip.feature.walletconnection.generated.resources.Res
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_api_key_label
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_api_key_placeholder
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_connect
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_description
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_hide_api_key
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_paste
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_show_api_key
import xyz.lilsus.blip.feature.walletconnection.generated.resources.add_blink_wallet_title

data class NativeBlinkWalletConnectionText(
    val title: String,
    val description: String,
    val apiKeyLabel: String,
    val apiKeyPlaceholder: String,
    val showApiKey: String,
    val hideApiKey: String,
    val paste: String,
    val connect: String
)

suspend fun nativeBlinkWalletConnectionText(): NativeBlinkWalletConnectionText =
    NativeBlinkWalletConnectionText(
        title = getString(Res.string.add_blink_wallet_title),
        description = getString(Res.string.add_blink_wallet_description),
        apiKeyLabel = getString(Res.string.add_blink_wallet_api_key_label),
        apiKeyPlaceholder = getString(Res.string.add_blink_wallet_api_key_placeholder),
        showApiKey = getString(Res.string.add_blink_wallet_show_api_key),
        hideApiKey = getString(Res.string.add_blink_wallet_hide_api_key),
        paste = getString(Res.string.add_blink_wallet_paste),
        connect = getString(Res.string.add_blink_wallet_connect)
    )
