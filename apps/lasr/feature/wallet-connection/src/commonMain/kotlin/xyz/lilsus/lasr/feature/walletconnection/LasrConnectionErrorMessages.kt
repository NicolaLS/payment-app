package xyz.lilsus.lasr.feature.walletconnection

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.Res
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.connect_wallet_required_methods
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.error_invalid_wallet_uri
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.error_relay_connection_failed
import xyz.lilsus.lasr.feature.walletconnection.generated.resources.error_wallet_already_connected
import xyz.lilsus.lasr.integration.nwc.NwcConnectionError

@Composable
fun lasrConnectionErrorMessageFor(error: NwcConnectionError): String = when (error) {
    NwcConnectionError.AlreadyConnected ->
        stringResource(Res.string.error_wallet_already_connected)

    NwcConnectionError.InvalidUri ->
        stringResource(Res.string.error_invalid_wallet_uri)

    NwcConnectionError.RequiredMethodsMissing ->
        stringResource(Res.string.connect_wallet_required_methods)

    is NwcConnectionError.ConnectionFailed ->
        stringResource(Res.string.error_relay_connection_failed)
}
