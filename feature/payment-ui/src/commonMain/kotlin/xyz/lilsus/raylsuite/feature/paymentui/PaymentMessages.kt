package xyz.lilsus.raylsuite.feature.paymentui

import org.jetbrains.compose.resources.getString
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.toast_bitcoin_address
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.toast_bolt12_not_supported
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.toast_lnurl_request_not_supported
import xyz.lilsus.raylsuite.feature.paymentui.generated.resources.toast_payment_link_not_supported

suspend fun PaymentToastMessage.localizedMessage(): String = when (this) {
    PaymentToastMessage.BitcoinAddressNotSupported ->
        getString(Res.string.toast_bitcoin_address)

    PaymentToastMessage.Bolt12NotSupported ->
        getString(Res.string.toast_bolt12_not_supported)

    PaymentToastMessage.LnurlRequestNotSupported ->
        getString(Res.string.toast_lnurl_request_not_supported)

    PaymentToastMessage.PaymentLinkNotSupported ->
        getString(Res.string.toast_payment_link_not_supported)
}
