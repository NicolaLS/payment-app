package xyz.lilsus.papp.domain.lnurl

import fr.acinq.bitcoin.Bech32
import kotlin.math.min
import xyz.lilsus.papp.domain.util.decodeUrlComponent

class LightningInputParser {

    fun parse(raw: String): ParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ParseResult.Failure(FailureReason.Empty)
        }
        return parseInternal(trimmed, allowBitcoinScheme = true)
    }

    sealed class ParseResult {
        data class Success(val target: Target) : ParseResult()
        data class Failure(val reason: FailureReason) : ParseResult()
    }

    /**
     * Describes why parsing failed. Used to show appropriate feedback to the user.
     */
    sealed class FailureReason {
        /** Input is empty or blank. */
        data object Empty : FailureReason()

        /** Input is a Bitcoin on-chain address (not Lightning). */
        data object BitcoinAddress : FailureReason()

        /** Input is a BOLT12 offer which is not yet supported. */
        data object Bolt12Offer : FailureReason()

        /** Input doesn't match any known Lightning or Bitcoin format. */
        data object Unrecognized : FailureReason()
    }

    sealed class Target {
        data class Lnurl(val endpoint: String) : Target()
        data class LightningAddressTarget(val address: LightningAddress) : Target()
        data class Bolt11Candidate(val invoice: String) : Target()
    }

    private fun parseInternal(value: String, allowBitcoinScheme: Boolean): ParseResult {
        var current = value.trim()
        if (current.startsWith("lightning:", ignoreCase = true)) {
            current = current.substringAfter(':')
        }

        if (allowBitcoinScheme && current.startsWith("bitcoin:", ignoreCase = true)) {
            val withoutScheme = current.substring(8)
            val queryIndex = withoutScheme.indexOf('?')
            if (queryIndex != -1 && queryIndex < withoutScheme.lastIndex) {
                val query = withoutScheme.substring(queryIndex + 1)
                val lightningParam = parseQuery(query)["lightning"]
                if (!lightningParam.isNullOrBlank()) {
                    val decoded = decodeUrlComponent(lightningParam)
                    return parseInternal(decoded, allowBitcoinScheme = false)
                }
            }

            val beforeQuery = if (queryIndex == -1) {
                withoutScheme
            } else {
                withoutScheme.substring(0, queryIndex)
            }
            if (looksLikeLnurl(beforeQuery)) {
                return parseInternal(beforeQuery, allowBitcoinScheme = false)
            }

            // bitcoin: URI without lightning param - it's an on-chain address
            return ParseResult.Failure(FailureReason.BitcoinAddress)
        }

        if (looksLikeLightningAddress(current)) {
            val address = toLightningAddress(current)
                ?: return ParseResult.Failure(FailureReason.Unrecognized)
            return ParseResult.Success(Target.LightningAddressTarget(address))
        }

        val schemeStripped = current.substringAfter("://", current)
        val hostCandidate = schemeStripped.substringBefore('/')
        if (hostCandidate != current && looksLikeLightningAddress(hostCandidate)) {
            val address = toLightningAddress(hostCandidate)
                ?: return ParseResult.Failure(FailureReason.Unrecognized)
            return ParseResult.Success(Target.LightningAddressTarget(address))
        }

        val lnurlEndpoint = when {
            // Check URL schemes first (before bech32) to avoid misclassification
            current.startsWith(
                "lnurlp://",
                ignoreCase = true
            ) -> convertSchemeToHttps(current, "lnurlp")

            current.startsWith(
                "lnurlw://",
                ignoreCase = true
            ) -> convertSchemeToHttps(current, "lnurlw")

            current.startsWith(
                "lnurl://",
                ignoreCase = true
            ) -> convertSchemeToHttps(current, "lnurl")

            // Then check for bech32-encoded LNURL
            looksLikeLnurl(current) -> decodeBech32Lnurl(current)

            else -> null
        }
        if (lnurlEndpoint != null) {
            return ParseResult.Success(Target.Lnurl(lnurlEndpoint))
        }

        // Check for BOLT12 offers (not yet supported)
        if (looksLikeBolt12Offer(current)) {
            return ParseResult.Failure(FailureReason.Bolt12Offer)
        }

        // Only treat as BOLT11 candidate if it looks like one (starts with "ln")
        if (looksLikeBolt11(current)) {
            return ParseResult.Success(Target.Bolt11Candidate(current))
        }

        // Check for standalone bitcoin addresses (without bitcoin: scheme)
        if (looksLikeBitcoinAddress(current)) {
            return ParseResult.Failure(FailureReason.BitcoinAddress)
        }

        return ParseResult.Failure(FailureReason.Unrecognized)
    }

    private fun looksLikeLnurl(value: String): Boolean {
        if (value.length < 6) return false
        val prefix = value.substring(0, min(6, value.length)).lowercase()
        return prefix.startsWith("lnurl")
    }

    /**
     * Checks if the input looks like a BOLT11 invoice (starts with "ln" but not "lno").
     * This is a quick heuristic check - actual validation happens in Bolt11InvoiceParser.
     */
    private fun looksLikeBolt11(value: String): Boolean {
        if (value.length < 2) return false
        val lower = value.lowercase()
        // Exclude BOLT12 offers which start with "lno"
        if (lower.startsWith("lno")) return false
        return lower.startsWith("ln")
    }

    /**
     * Checks if the input looks like a BOLT12 offer (starts with "lno1").
     */
    private fun looksLikeBolt12Offer(value: String): Boolean =
        value.length >= 4 && value.lowercase().startsWith("lno1")

    /**
     * Checks if the input looks like a Bitcoin address.
     * Covers legacy (1..., 3...), SegWit (bc1q...), and Taproot (bc1p...) addresses.
     */
    private fun looksLikeBitcoinAddress(value: String): Boolean {
        val lower = value.lowercase()
        // Bech32/Bech32m (SegWit and Taproot)
        if (lower.startsWith("bc1")) return value.length in 42..62
        // Legacy P2PKH (starts with 1)
        if (value.startsWith("1")) return value.length in 25..34
        // Legacy P2SH (starts with 3)
        if (value.startsWith("3")) return value.length in 25..34
        return false
    }

    private fun decodeBech32Lnurl(value: String): String? {
        return runCatching {
            val (hrp, data, _) = Bech32.decode(value)
            if (!hrp.equals("lnurl", ignoreCase = true)) {
                return@runCatching null
            }
            val bytes = convert5BitTo8Bit(data) ?: return@runCatching null
            bytes.decodeToString()
        }.getOrNull()
    }

    private fun convertSchemeToHttps(value: String, scheme: String): String? {
        val withoutScheme = value.substringAfter("://", missingDelimiterValue = "")
        if (withoutScheme.isEmpty()) return null
        val isOnion = withoutScheme.contains(".onion", ignoreCase = true)
        val protocol = if (isOnion) "http" else "https"
        return "$protocol://$withoutScheme"
    }

    private fun convert5BitTo8Bit(data: Array<Byte>): ByteArray? {
        var accumulator = 0
        var bits = 0
        val output = ArrayList<Byte>((data.size * 5) / 8)
        data.forEach { word ->
            val value = (word.toInt() and 0xFF) and 0x1F
            accumulator = (accumulator shl 5) or value
            bits += 5
            while (bits >= 8) {
                bits -= 8
                val byte = (accumulator shr bits) and 0xFF
                output.add(byte.toByte())
            }
        }
        if (bits in 1..7) {
            val padding = (accumulator shl (8 - bits)) and 0xFF
            if (padding != 0) return null
        }
        return output.toByteArray()
    }

    private fun looksLikeLightningAddress(raw: String): Boolean {
        if (!raw.contains('@')) return false
        val parts = raw.split('@')
        if (parts.size != 2) return false
        val (userPart, domainPart) = parts
        if (userPart.isEmpty() || domainPart.isEmpty()) return false
        val usernameValid = userPart.all {
            it.isLowerCaseLetterOrDigit() ||
                it in setOf('-', '_', '.', '+')
        }
        val domainValid = domainPart.all { it.isLowerCaseLetterOrDigit() || it == '-' || it == '.' }
        return usernameValid && domainValid && domainPart.contains('.')
    }

    private fun Char.isLowerCaseLetterOrDigit(): Boolean = this in 'a'..'z' || this in '0'..'9'

    private fun toLightningAddress(raw: String): LightningAddress? {
        val parts = raw.lowercase().split('@')
        if (parts.size != 2) return null
        val (userPart, domainPart) = parts
        if (userPart.isEmpty() || domainPart.isEmpty()) return null
        val tagIndex = userPart.indexOf('+')
        val (username, tag) = if (tagIndex >= 0) {
            userPart.substring(0, tagIndex) to userPart.substring(tagIndex + 1).ifEmpty { null }
        } else {
            userPart to null
        }
        if (username.isEmpty()) return null
        return LightningAddress(username = username, domain = domainPart, tag = tag)
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split('&')
            .mapNotNull { pair ->
                if (pair.isEmpty()) return@mapNotNull null
                val (key, value) = pair.split('=', limit = 2).let { parts ->
                    when (parts.size) {
                        2 -> parts[0] to parts[1]
                        1 -> parts[0] to ""
                        else -> return@let null
                    }
                } ?: return@mapNotNull null
                decodeUrlComponent(key).lowercase() to decodeUrlComponent(value)
            }
            .toMap()
    }
}
