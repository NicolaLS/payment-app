package xyz.lilsus.raylsuite.core.payment

import kotlin.jvm.JvmInline
import xyz.lilsus.raylsuite.core.model.LightningAddress

@JvmInline
value class DynamicPaymentSourceKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Dynamic payment source key must not be blank" }
    }
}

fun lnurlDynamicPaymentSourceKey(endpoint: String): DynamicPaymentSourceKey {
    val trimmed = endpoint.trim()
    val schemeSeparator = trimmed.indexOf("://")
    if (schemeSeparator < 0) return DynamicPaymentSourceKey("lnurl:$trimmed")

    val authorityStart = schemeSeparator + SCHEME_SEPARATOR_LENGTH
    val authorityEnd =
        trimmed.indexOfAny(charArrayOf('/', '?', '#'), startIndex = authorityStart)
            .takeIf { it >= 0 }
            ?: trimmed.length
    val scheme = trimmed.substring(0, schemeSeparator).lowercase()
    val authority = trimmed.substring(authorityStart, authorityEnd).lowercase()
    return DynamicPaymentSourceKey(
        "lnurl:$scheme://$authority${trimmed.substring(authorityEnd)}"
    )
}

fun lightningAddressDynamicPaymentSourceKey(address: LightningAddress): DynamicPaymentSourceKey =
    DynamicPaymentSourceKey("lud16:${address.full.lowercase()}")

private const val SCHEME_SEPARATOR_LENGTH = 3
