package xyz.lilsus.raylsuite.integration.lnurl

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** LNURL uses public HTTPS services; local services and Tor transport are not supported. */
internal fun parseLnurlDestination(value: String): Url? {
    if (value.length > 8_192 ||
        value.any { it.isWhitespace() || it == '\\' || it.code < 0x20 }
    ) {
        return null
    }
    val url = runCatching { Url(value) }.getOrNull() ?: return null
    if (url.protocol != URLProtocol.HTTPS || url.user != null || url.password != null ||
        url.fragment.isNotEmpty()
    ) {
        return null
    }
    val host = url.host.lowercase()
    val labels = host.split('.')
    if (host.length > 253 || labels.size < 2 ||
        labels.any { !DOMAIN_LABEL.matches(it) }
    ) {
        return null
    }
    // Reject IP literals, including alternate numeric notations interpreted differently by OS resolvers.
    if (!TOP_LEVEL_DOMAIN.matches(labels.last())) return null
    if (LOCAL_SUFFIXES.any { host == it || host.endsWith(".$it") }) return null
    return url
}

/** Checks every DNS answer, including embedded IPv4 in mapped and well-known NAT64 addresses. */
internal fun isPublicLnurlAddress(address: ByteArray): Boolean {
    val bytes = address.map { it.toInt() and 0xff }
    if (bytes.size == 4) {
        val (a, b, c) = bytes
        return when {
            a in setOf(0, 10, 127) || a >= 224 -> false
            a == 100 && b in 64..127 -> false
            a == 169 && b == 254 -> false
            a == 172 && b in 16..31 -> false
            a == 192 && b == 168 -> false
            a == 192 && b == 0 && c in setOf(0, 2) -> false
            a == 192 && b == 88 && c == 99 -> false
            a == 198 && b in 18..19 -> false
            a == 198 && b == 51 && c == 100 -> false
            a == 203 && b == 0 && c == 113 -> false
            else -> true
        }
    }
    if (bytes.size != 16) return false
    val isMapped = bytes.take(10).all { it == 0 } && bytes[10] == 0xff && bytes[11] == 0xff
    val isWellKnownNat64 = bytes.take(12) == listOf(0x00, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0)
    if (isMapped || isWellKnownNat64) return isPublicLnurlAddress(address.copyOfRange(12, 16))
    // Global unicast only. Exclude protocol assignments, documentation, and 6to4 tunnels.
    return when {
        bytes[0] !in 0x20..0x3f -> false
        bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] < 2 -> false
        bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8 -> false
        bytes[0] == 0x20 && bytes[1] == 0x02 -> false
        bytes[0] == 0x3f && bytes[1] == 0xff && bytes[2] < 0x10 -> false
        else -> true
    }
}

internal expect suspend fun resolveLnurlHost(host: String): List<ByteArray>

private val DOMAIN_LABEL = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
private val TOP_LEVEL_DOMAIN = Regex("(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})")
private val LOCAL_SUFFIXES = setOf(
    "localhost", "local", "localdomain", "internal", "lan", "home", "home.arpa",
    "onion", "invalid", "test", "example"
)
