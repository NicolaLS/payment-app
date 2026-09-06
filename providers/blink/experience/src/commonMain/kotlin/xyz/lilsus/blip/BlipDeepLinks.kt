package xyz.lilsus.blip

import xyz.lilsus.blip.feature.payment.PaymentDeepLinkEvents

object BlipDeepLinks {
    fun clear() = PaymentDeepLinkEvents.clear()

    fun emit(uri: String) {
        PaymentDeepLinkEvents.emit(uri)
    }
}
