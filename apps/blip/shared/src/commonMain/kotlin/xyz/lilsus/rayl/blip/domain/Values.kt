package xyz.lilsus.rayl.blip.domain

import fr.acinq.bitcoin.ByteVector32
import kotlin.jvm.JvmInline

@JvmInline
value class ConnectionId private constructor(val value: String) {
    companion object {
        fun parse(value: String): ConnectionId? =
            value.trim().takeIf(::isOpaqueId)?.let(::ConnectionId)

        fun require(value: String): ConnectionId =
            requireNotNull(parse(value)) { "Invalid connection id" }
    }
}

@JvmInline
value class AttemptId private constructor(val value: String) {
    companion object {
        fun parse(value: String): AttemptId? = value.trim().takeIf(::isOpaqueId)?.let(::AttemptId)

        fun require(value: String): AttemptId =
            requireNotNull(parse(value)) { "Invalid attempt id" }
    }
}

@JvmInline
value class ContactId private constructor(val value: String) {
    companion object {
        fun parse(value: String): ContactId? = value.trim().takeIf(::isOpaqueId)?.let(::ContactId)

        fun require(value: String): ContactId =
            requireNotNull(parse(value)) { "Invalid contact id" }
    }
}

@JvmInline
value class ShortcutId private constructor(val value: String) {
    companion object {
        fun parse(value: String): ShortcutId? = value.trim().takeIf(::isOpaqueId)?.let(::ShortcutId)

        fun require(value: String): ShortcutId =
            requireNotNull(parse(value)) { "Invalid shortcut id" }
    }
}

@JvmInline
value class BlinkAccountId private constructor(val value: String) {
    companion object {
        fun parse(value: String): BlinkAccountId? =
            value.trim().takeIf(::isProviderId)?.let(::BlinkAccountId)

        fun require(value: String): BlinkAccountId =
            requireNotNull(parse(value)) { "Invalid Blink account id" }
    }
}

@JvmInline
value class BlinkWalletId private constructor(val value: String) {
    companion object {
        fun parse(value: String): BlinkWalletId? =
            value.trim().takeIf(::isProviderId)?.let(::BlinkWalletId)

        fun require(value: String): BlinkWalletId =
            requireNotNull(parse(value)) { "Invalid Blink wallet id" }
    }
}

class BlinkApiKey private constructor(private val value: String) {
    internal inline fun <T> use(block: (String) -> T): T = block(value)

    override fun toString(): String = "BlinkApiKey(**redacted**)"

    companion object {
        fun parse(value: String): BlinkApiKey? = value.trim()
            .takeIf { it.length in 8..4_096 && it.none(Char::isISOControl) }
            ?.let(::BlinkApiKey)
    }
}

data class PaymentHash(val bytes: ByteVector32) {
    val hex: String
        get() = bytes.toHex()

    companion object {
        fun parse(value: String): PaymentHash? =
            runCatching { PaymentHash(ByteVector32.fromValidHex(value.trim())) }.getOrNull()
    }
}

interface IdentifierSource {
    fun newConnectionId(): ConnectionId
    fun newAttemptId(): AttemptId
    fun newContactId(): ContactId
    fun newShortcutId(): ShortcutId
}

interface AppClock {
    fun nowMillis(): Long
    fun nowSeconds(): Long = nowMillis() / 1_000L
}

private fun isOpaqueId(value: String): Boolean = value.length in 16..80 &&
    value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

private fun isProviderId(value: String): Boolean =
    value.length in 1..256 && value.none(Char::isISOControl)
