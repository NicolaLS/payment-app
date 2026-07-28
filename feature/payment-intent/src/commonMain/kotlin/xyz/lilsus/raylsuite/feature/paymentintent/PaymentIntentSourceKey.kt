package xyz.lilsus.raylsuite.feature.paymentintent

import kotlin.jvm.JvmInline
import xyz.lilsus.raylsuite.core.model.LightningAddress

@JvmInline
value class PaymentIntentSourceKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Payment intent source key must not be blank" }
    }
}

fun lnurlPaymentIntentSourceKey(endpoint: String): PaymentIntentSourceKey {
    val trimmed = endpoint.trim()
    val schemeSeparator = trimmed.indexOf("://")
    if (schemeSeparator < 0) return PaymentIntentSourceKey("lnurl:$trimmed")

    val authorityStart = schemeSeparator + SCHEME_SEPARATOR_LENGTH
    val authorityEnd =
        trimmed.indexOfAny(charArrayOf('/', '?', '#'), startIndex = authorityStart)
            .takeIf { it >= 0 }
            ?: trimmed.length
    val scheme = trimmed.substring(0, schemeSeparator).lowercase()
    val authority = trimmed.substring(authorityStart, authorityEnd).lowercase()
    return PaymentIntentSourceKey(
        "lnurl:$scheme://$authority${trimmed.substring(authorityEnd)}"
    )
}

fun lightningAddressPaymentIntentSourceKey(address: LightningAddress): PaymentIntentSourceKey =
    PaymentIntentSourceKey("lud16:${address.full.lowercase()}")

private const val SCHEME_SEPARATOR_LENGTH = 3
