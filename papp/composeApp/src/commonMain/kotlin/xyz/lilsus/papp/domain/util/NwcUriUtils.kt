package xyz.lilsus.papp.domain.util

fun parseWalletPublicKey(uri: String): String {
    val trimmed = uri.trim()
    val schemeSeparator = trimmed.indexOf("://")
    val remainder = if (schemeSeparator >= 0) {
        trimmed.substring(schemeSeparator + 3)
    } else {
        val colonIndex = trimmed.indexOf(':')
        require(colonIndex > 0) { "Nostr Wallet Connect URI missing scheme" }
        trimmed.substring(colonIndex + 1)
    }
    val withoutSlashes = remainder.removePrefix("//")
    val pubKeyPart = withoutSlashes.substringBefore('?')
    require(pubKeyPart.isNotBlank()) { "Nostr Wallet Connect URI missing wallet public key" }
    return pubKeyPart.lowercase()
}
