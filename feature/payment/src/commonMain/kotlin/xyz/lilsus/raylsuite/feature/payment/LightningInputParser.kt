package xyz.lilsus.raylsuite.feature.payment

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
import xyz.lilsus.raylsuite.core.model.LightningAddress

class LightningInputParser(private val nowSeconds: () -> Long = ::currentTimestampSeconds) {
    fun parse(raw: String): ParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ParseResult.Failure(FailureReason.Empty)
        }
        return parseInternal(trimmed, allowBitcoinScheme = true)
    }

    sealed interface ParseResult {
        data class Success(val target: Target) : ParseResult

        data class Failure(val reason: FailureReason) : ParseResult
    }

    sealed interface FailureReason {
        data object Empty : FailureReason

        data object BitcoinAddress : FailureReason

        data object Bolt12 : FailureReason

        data class InvalidInvoice(val reason: String?) : FailureReason

        data object ExpiredInvoice : FailureReason

        data object Unrecognized : FailureReason
    }

    sealed interface Target {
        data class Lnurl(val endpoint: String) : Target

        data class LightningAddressTarget(val address: LightningAddress) : Target

        data class Bolt11(val invoice: Bolt11Invoice) : Target
    }

    private fun parseInternal(value: String, allowBitcoinScheme: Boolean): ParseResult {
        var current = value.trim()

        if (current.startsWith("lightning:", ignoreCase = true)) {
            current = current.substringAfter(':')
        }

        if (allowBitcoinScheme && current.startsWith("bitcoin:", ignoreCase = true)) {
            val withoutScheme = current.substring(BITCOIN_SCHEME_LENGTH)
            val queryIndex = withoutScheme.indexOf('?')
            if (queryIndex != -1 && queryIndex < withoutScheme.lastIndex) {
                val query = withoutScheme.substring(queryIndex + 1)
                val lightningParam = parseQuery(query)["lightning"]
                if (!lightningParam.isNullOrBlank()) {
                    return parseInternal(lightningParam, allowBitcoinScheme = false)
                }
            }

            val beforeQuery =
                if (queryIndex == -1) {
                    withoutScheme
                } else {
                    withoutScheme.substring(0, queryIndex)
                }
            if (looksLikeLnurl(beforeQuery)) {
                return parseInternal(beforeQuery, allowBitcoinScheme = false)
            }
            return ParseResult.Failure(FailureReason.BitcoinAddress)
        }

        LightningAddress.parse(current)?.let { address ->
            return ParseResult.Success(Target.LightningAddressTarget(address))
        }

        val lnurlEndpoint =
            when {
                current.startsWith("lnurlp://", ignoreCase = true) ->
                    convertSchemeToHttp(current, "lnurlp")

                current.startsWith("lnurlw://", ignoreCase = true) ->
                    convertSchemeToHttp(current, "lnurlw")

                current.startsWith("lnurl://", ignoreCase = true) ->
                    convertSchemeToHttp(current, "lnurl")

                looksLikeLnurl(current) -> decodeBech32Lnurl(current)

                current.startsWith("https://", ignoreCase = true) ||
                    current.startsWith("http://", ignoreCase = true) ->
                    current.takeIf(::looksLikeLnurlHttpUrl)

                else -> null
            }
        if (lnurlEndpoint != null) {
            return ParseResult.Success(Target.Lnurl(lnurlEndpoint))
        }

        if (OfferTypes.Offer.decode(current) is Try.Success) {
            return ParseResult.Failure(FailureReason.Bolt12)
        }

        if (looksLikePaymentRequest(current)) {
            return when (val decoded = PaymentRequest.read(current)) {
                is Try.Success ->
                    when (val request = decoded.result) {
                        is Bolt11Invoice ->
                            if (request.isExpired(nowSeconds())) {
                                ParseResult.Failure(FailureReason.ExpiredInvoice)
                            } else {
                                ParseResult.Success(Target.Bolt11(request))
                            }

                        is Bolt12Invoice -> ParseResult.Failure(FailureReason.Bolt12)
                    }

                is Try.Failure ->
                    ParseResult.Failure(
                        FailureReason.InvalidInvoice(decoded.error.message)
                    )
            }
        }

        if (looksLikeBitcoinAddress(current)) {
            return ParseResult.Failure(FailureReason.BitcoinAddress)
        }

        return ParseResult.Failure(FailureReason.Unrecognized)
    }

    private fun looksLikeLnurl(value: String): Boolean {
        if (value.length < MIN_LNURL_PREFIX_LENGTH) return false
        val prefix = value.substring(0, min(MIN_LNURL_PREFIX_LENGTH, value.length)).lowercase()
        return prefix.startsWith("lnurl")
    }

    private fun looksLikePaymentRequest(value: String): Boolean =
        value.startsWith("ln", ignoreCase = true)

    private fun looksLikeBitcoinAddress(value: String): Boolean =
        SUPPORTED_BITCOIN_CHAINS.any { chain ->
            Bitcoin.addressToPublicKeyScript(chain.chainHash, value).isRight
        }

    private fun decodeBech32Lnurl(value: String): String? = runCatching {
        val (humanReadablePart, data, _) = Bech32.decode(value)
        if (!humanReadablePart.equals("lnurl", ignoreCase = true)) {
            return@runCatching null
        }
        Bech32.five2eight(data, 0).decodeToString()
    }.getOrNull()

    private fun convertSchemeToHttp(value: String, scheme: String): String? {
        val withoutScheme = value.substringAfter("$scheme://", missingDelimiterValue = "")
        if (withoutScheme.isEmpty()) return null
        val protocol =
            if (withoutScheme.contains(".onion", ignoreCase = true)) {
                "http"
            } else {
                "https"
            }
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

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query
            .split('&')
            .mapNotNull { pair ->
                if (pair.isEmpty()) return@mapNotNull null
                val parts = pair.split('=', limit = 2)
                val key = parts.firstOrNull() ?: return@mapNotNull null
                val value = parts.getOrNull(1).orEmpty()
                Uri.decode(key, convertPlus = true).lowercase() to
                    Uri.decode(value, convertPlus = true)
            }.toMap()
    }

    private companion object {
        const val BITCOIN_SCHEME_LENGTH = 8
        const val MIN_LNURL_PREFIX_LENGTH = 6

        val SUPPORTED_BITCOIN_CHAINS =
            listOf(
                Chain.Mainnet,
                Chain.Testnet3,
                Chain.Testnet4,
                Chain.Signet,
                Chain.Regtest
            )
    }
}
