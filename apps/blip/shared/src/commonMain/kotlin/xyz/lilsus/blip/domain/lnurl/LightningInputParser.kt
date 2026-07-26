package xyz.lilsus.blip.domain.lnurl

import com.eygraber.uri.Uri
import fr.acinq.bitcoin.Bech32
import fr.acinq.bitcoin.Bitcoin
import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.utils.Try
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.payment.Bolt12Invoice
import fr.acinq.lightning.payment.PaymentRequest
import fr.acinq.lightning.utils.currentTimestampSeconds
import fr.acinq.lightning.wire.OfferTypes
import kotlin.math.min

class LightningInputParser(private val nowSeconds: () -> Long = ::currentTimestampSeconds) {

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

        /** Input is a BOLT12 offer or invoice, which is not yet supported. */
        data object Bolt12 : FailureReason()

        /** Input resembles a BOLT11 invoice but ACINQ rejected it. */
        data class InvalidInvoice(val reason: String?) : FailureReason()

        /** Input is a valid BOLT11 invoice that has expired. */
        data object ExpiredInvoice : FailureReason()

        /** Input is a NWC wallet URI (should be added via Settings, not payment screen). */
        data class NwcWalletUri(val uri: String) : FailureReason()

        /** Input doesn't match any known Lightning or Bitcoin format. */
        data object Unrecognized : FailureReason()
    }

    sealed class Target {
        data class Lnurl(val endpoint: String) : Target()
        data class LightningAddressTarget(val address: LightningAddress) : Target()
        data class Bolt11(val invoice: Bolt11Invoice) : Target()
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
                    return parseInternal(lightningParam, allowBitcoinScheme = false)
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

        val urlWrappedAddress = parseUrlWrappedLightningAddress(current)
        if (urlWrappedAddress != null) {
            return ParseResult.Success(Target.LightningAddressTarget(urlWrappedAddress))
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

            // Only treat raw HTTP(S) URLs as LNURL when they contain LNURL-specific hints.
            // This avoids latching onto generic website URLs from random QR codes.
            current.startsWith("https://", ignoreCase = true) ||
                current.startsWith("http://", ignoreCase = true) ->
                current.takeIf(::looksLikeLnurlHttpUrl)

            else -> null
        }
        if (lnurlEndpoint != null) {
            return ParseResult.Success(Target.Lnurl(lnurlEndpoint))
        }

        // Decode BOLT12 with ACINQ instead of classifying on a prefix alone.
        if (OfferTypes.Offer.decode(current) is Try.Success) {
            return ParseResult.Failure(FailureReason.Bolt12)
        }

        // ACINQ owns payment-request selection, decoding, validation, and models.
        if (looksLikePaymentRequest(current)) {
            return when (val decoded = PaymentRequest.read(current)) {
                is Try.Success -> when (val request = decoded.result) {
                    is Bolt11Invoice -> if (request.isExpired(nowSeconds())) {
                        ParseResult.Failure(FailureReason.ExpiredInvoice)
                    } else {
                        ParseResult.Success(Target.Bolt11(request))
                    }

                    is Bolt12Invoice -> ParseResult.Failure(FailureReason.Bolt12)
                }

                is Try.Failure -> ParseResult.Failure(
                    FailureReason.InvalidInvoice(decoded.error.message)
                )
            }
        }

        // Check for standalone bitcoin addresses (without bitcoin: scheme)
        if (looksLikeBitcoinAddress(current)) {
            return ParseResult.Failure(FailureReason.BitcoinAddress)
        }

        // Check for NWC wallet URI - users might try to scan wallet QRs on payment screen
        // This is checked last since payments are the primary use case
        if (looksLikeNwcUri(value)) {
            return ParseResult.Failure(FailureReason.NwcWalletUri(value))
        }

        return ParseResult.Failure(FailureReason.Unrecognized)
    }

    private fun looksLikeLnurl(value: String): Boolean {
        if (value.length < 6) return false
        val prefix = value.substring(0, min(6, value.length)).lowercase()
        return prefix.startsWith("lnurl")
    }

    private fun looksLikePaymentRequest(value: String): Boolean =
        value.startsWith("ln", ignoreCase = true)

    /**
     * Checks if the input looks like a NWC wallet connection URI.
     * Handles both nostr+walletconnect:// and nostr+walletconnect: formats.
     */
    private fun looksLikeNwcUri(value: String): Boolean =
        value.startsWith("nostr+walletconnect:", ignoreCase = true)

    private fun looksLikeBitcoinAddress(value: String): Boolean =
        SUPPORTED_BITCOIN_CHAINS.any { chain ->
            Bitcoin.addressToPublicKeyScript(chain.chainHash, value).isRight
        }

    private fun decodeBech32Lnurl(value: String): String? {
        return runCatching {
            val (hrp, data, _) = Bech32.decode(value)
            if (!hrp.equals("lnurl", ignoreCase = true)) {
                return@runCatching null
            }
            Bech32.five2eight(data, 0).decodeToString()
        }.getOrNull()
    }

    private fun parseUrlWrappedLightningAddress(value: String): LightningAddress? {
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        val withoutScheme = value.substringAfter("://", missingDelimiterValue = "")
        if (withoutScheme.isEmpty()) return null
        if (withoutScheme.contains('?') || withoutScheme.contains('#')) return null

        val authority = withoutScheme.substringBefore('/')
        val path = withoutScheme.substringAfter('/', missingDelimiterValue = "")
        if (authority.isEmpty()) return null
        if (path.isNotEmpty()) return null
        if (authority.count { it == '@' } != 1) return null

        val user = authority.substringBefore('@')
        val host = authority.substringAfter('@')
        if (user.isEmpty() || host.isEmpty()) return null
        if (user.contains(':')) return null

        val candidate = "$user@$host"
        if (!looksLikeLightningAddress(candidate)) return null
        return toLightningAddress(candidate)
    }

    private fun convertSchemeToHttps(value: String, scheme: String): String? {
        val withoutScheme = value.substringAfter("://", missingDelimiterValue = "")
        if (withoutScheme.isEmpty()) return null
        val isOnion = withoutScheme.contains(".onion", ignoreCase = true)
        val protocol = if (isOnion) "http" else "https"
        return "$protocol://$withoutScheme"
    }

    private fun looksLikeLnurlHttpUrl(value: String): Boolean {
        val lower = value.lowercase()
        return lower.contains("/.well-known/lnurlp/") ||
            lower.contains("/lnurlp/") ||
            lower.contains("/lnurl/") ||
            lower.contains("tag=payrequest") ||
            lower.contains("lnurl=") ||
            lower.contains("lightning=")
    }

    private fun looksLikeLightningAddress(raw: String): Boolean {
        val candidate = raw.trim()
        if (!candidate.contains('@')) return false
        if (candidate.contains("://")) return false
        if (candidate.any { it == '/' || it == '?' || it == '#' }) return false
        if (candidate.count { it == '@' } != 1) return false

        val parts = candidate.split('@', limit = 2)
        if (parts.size != 2) return false
        val (userPart, domainPartRaw) = parts
        if (userPart.isEmpty() || domainPartRaw.isEmpty()) return false

        val usernameValid = userPart.lowercase().all {
            it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' || it == '+'
        }
        if (!usernameValid) return false

        return isValidDomain(domainPartRaw.lowercase())
    }

    private fun isValidDomain(domain: String): Boolean {
        if (domain.length > MAX_DOMAIN_LENGTH) return false
        val labels = domain.split('.')
        if (labels.size < 2) return false
        val validLabels = labels.all { label ->
            label.isNotEmpty() &&
                label.length <= MAX_DOMAIN_LABEL_LENGTH &&
                !label.startsWith('-') &&
                !label.endsWith('-') &&
                label.all { it.isLetterOrDigit() || it == '-' }
        }
        if (!validLabels) return false
        return labels.last().any { it in 'a'..'z' }
    }

    private fun Char.isLetterOrDigit(): Boolean = this in 'a'..'z' || this in '0'..'9'

    private fun toLightningAddress(raw: String): LightningAddress? {
        if (!looksLikeLightningAddress(raw)) return null
        val parts = raw.trim().split('@', limit = 2)
        if (parts.size != 2) return null
        val (userPart, domainPartRaw) = parts
        if (userPart.isEmpty() || domainPartRaw.isEmpty()) return null
        val domainPart = domainPartRaw.lowercase()
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
                Uri.decode(key, convertPlus = true).lowercase() to Uri.decode(
                    value,
                    convertPlus = true
                )
            }
            .toMap()
    }

    companion object {
        private const val MAX_DOMAIN_LENGTH = 253
        private const val MAX_DOMAIN_LABEL_LENGTH = 63

        private val SUPPORTED_BITCOIN_CHAINS = listOf(
            Chain.Mainnet,
            Chain.Testnet3,
            Chain.Testnet4,
            Chain.Signet,
            Chain.Regtest
        )
    }
}
