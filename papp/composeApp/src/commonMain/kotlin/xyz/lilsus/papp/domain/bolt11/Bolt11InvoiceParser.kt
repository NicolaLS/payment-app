package xyz.lilsus.papp.domain.bolt11

import fr.acinq.bitcoin.Bech32

/**
 * Minimal BOLT11 parser that extracts the optional amount (in millisatoshis) and the memo/description.
 */
open class Bolt11InvoiceParser {

    open fun parse(invoice: String): Bolt11ParseResult {
        val trimmed = invoice.trim()
        if (trimmed.isEmpty()) {
            return Bolt11ParseResult.Failure("Invoice is blank")
        }

        val raw = trimmed.removeLightningPrefix()
        val (hrp, data, _) = try {
            Bech32.decode(raw)
        } catch (t: Throwable) {
            return Bolt11ParseResult.Failure("Invoice is not valid bech32")
        }

        val amount = when (val amountResult = parseAmount(hrp)) {
            is AmountResult.Success -> amountResult.amountMsats
            is AmountResult.Failure -> return Bolt11ParseResult.Failure(amountResult.reason)
        }

        val memo = when (val memoResult = parseMemo(data)) {
            is MemoResult.Success -> memoResult.memo
            is MemoResult.Failure -> return Bolt11ParseResult.Failure(memoResult.reason)
        }

        val canonicalInvoice = raw.lowercase()

        return Bolt11ParseResult.Success(
            Bolt11InvoiceSummary(
                paymentRequest = canonicalInvoice,
                amountMsats = amount,
                memo = memo,
            ),
        )
    }

    private sealed class AmountResult {
        data class Success(val amountMsats: Long?) : AmountResult()
        data class Failure(val reason: String) : AmountResult()
    }

    private sealed class MemoResult {
        data class Success(val memo: Bolt11Memo) : MemoResult()
        data class Failure(val reason: String) : MemoResult()
    }

    private fun parseAmount(hrp: String): AmountResult {
        if (!hrp.startsWith("ln", ignoreCase = true)) {
            return AmountResult.Failure("Invoice must start with 'ln'")
        }

        val withoutLn = hrp.drop(2)
        if (withoutLn.isEmpty()) {
            return AmountResult.Failure("Invoice prefix is missing")
        }

        val currencyLength = withoutLn.indexOfFirst { it.isDigit() }
            .takeIf { it >= 0 } ?: withoutLn.length

        if (currencyLength == 0) {
            return AmountResult.Failure("Currency prefix is missing")
        }

        val amountPart = withoutLn.drop(currencyLength)
        if (amountPart.isEmpty()) {
            return AmountResult.Success(null)
        }

        val lastChar = amountPart.last()
        val multiplier = if (lastChar.isLetter()) lastChar.lowercaseChar() else null
        val digitsPart = if (multiplier != null) amountPart.dropLast(1) else amountPart

        if (digitsPart.isEmpty() || digitsPart.any { !it.isDigit() }) {
            return AmountResult.Failure("Amount must be a positive integer")
        }

        val parsedDigits = parsePositiveLong(digitsPart)
            ?: return AmountResult.Failure("Amount is too large")

        val amountMsats = when (multiplier) {
            null -> parsedDigits.safeMultiply(MSATS_PER_BTC)
            'm' -> parsedDigits.safeMultiply(MSATS_PER_MILLI_BITCOIN)
            'u' -> parsedDigits.safeMultiply(MSATS_PER_MICRO_BITCOIN)
            'n' -> parsedDigits.safeMultiply(MSATS_PER_NANO_BITCOIN)
            'p' -> {
                if (parsedDigits % 10 != 0L) {
                    return AmountResult.Failure("Pico bitcoin amounts must be divisible by 10")
                }
                parsedDigits / 10
            }

            else -> return AmountResult.Failure("Unknown amount multiplier '$multiplier'")
        } ?: return AmountResult.Failure("Amount is too large")

        return AmountResult.Success(amountMsats)
    }

    private fun parseMemo(data: Array<Byte>): MemoResult {
        if (data.size < SIGNATURE_LENGTH_WORDS + TIMESTAMP_WORD_COUNT) {
            return MemoResult.Failure("Invoice payload is too short")
        }

        val words = data.asSequence()
            .map { it.toInt() and 0xFF }
            .toList()

        val payload = words.dropLast(SIGNATURE_LENGTH_WORDS)
        if (payload.size < TIMESTAMP_WORD_COUNT) {
            return MemoResult.Failure("Invoice payload is missing timestamp")
        }

        var index = TIMESTAMP_WORD_COUNT
        var descriptionText: String? = null
        var descriptionHash: ByteArray? = null

        while (index < payload.size) {
            if (index + 2 >= payload.size) {
                return MemoResult.Failure("Tagged field length is truncated")
            }
            val type = payload[index]
            val dataLength = (payload[index + 1] shl 5) or payload[index + 2]
            index += 3
            if (index + dataLength > payload.size) {
                return MemoResult.Failure("Tagged field exceeds invoice size")
            }
            val fieldWords = payload.subList(index, index + dataLength)
            when (type) {
                TYPE_DESCRIPTION -> if (descriptionText == null) {
                    val bytes = fiveBitWordsToBytes(fieldWords)
                        ?: return MemoResult.Failure("Description padding is invalid")
                    descriptionText = runCatching { bytes.decodeToString() }.getOrNull()
                        ?: return MemoResult.Failure("Description is not valid UTF-8")
                }

                TYPE_DESCRIPTION_HASH -> if (descriptionHash == null) {
                    val bytes = fiveBitWordsToBytes(fieldWords)
                        ?: return MemoResult.Failure("Description hash padding is invalid")
                    if (bytes.size != 32) {
                        return MemoResult.Failure("Description hash must be 32 bytes")
                    }
                    descriptionHash = bytes
                }
            }
            index += dataLength
        }

        val memo = when {
            descriptionText != null -> Bolt11Memo.Text(descriptionText)
            descriptionHash != null -> Bolt11Memo.HashOnly(descriptionHash)
            else -> Bolt11Memo.None
        }

        return MemoResult.Success(memo)
    }

    private fun parsePositiveLong(digits: String): Long? {
        var result = 0L
        for (char in digits) {
            val digit = char - '0'
            if (result > (Long.MAX_VALUE - digit) / 10) {
                return null
            }
            result = result * 10 + digit
        }
        return result
    }

    private fun Long.safeMultiply(factor: Long): Long? {
        if (this == 0L || factor == 0L) return 0L
        if (this > Long.MAX_VALUE / factor) return null
        return this * factor
    }

    private fun fiveBitWordsToBytes(words: List<Int>): ByteArray? {
        var accumulator = 0
        var bits = 0
        val output = ArrayList<Byte>((words.size * 5) / 8)

        for (word in words) {
            if (word !in 0..31) return null
            accumulator = (accumulator shl 5) or word
            bits += 5
            while (bits >= 8) {
                bits -= 8
                val value = (accumulator shr bits) and 0xFF
                output.add(value.toByte())
            }
        }

        if (bits in 1..7) {
            val padding = (accumulator shl (8 - bits)) and 0xFF
            if (padding != 0) return null
        }

        return output.toByteArray()
    }

    private fun String.removeLightningPrefix(): String {
        val trimmed = this.trim()
        val lower = trimmed.lowercase()
        return when {
            lower.startsWith("lightning:") -> trimmed.substringAfter(':')
            lower.startsWith("bitcoin:") -> {
                val afterPrefix = trimmed.substring(8)
                val queryStart = afterPrefix.indexOf('?')
                if (queryStart == -1) {
                    if (afterPrefix.startsWith("ln", ignoreCase = true)) {
                        afterPrefix
                    } else {
                        trimmed
                    }
                } else if (queryStart == afterPrefix.lastIndex) {
                    trimmed
                } else {
                    val query = afterPrefix.substring(queryStart + 1)
                    val params = query.split("&")
                    params.firstNotNullOfOrNull { param ->
                        val (key, value) = param.split("=", limit = 2).let { parts ->
                            when (parts.size) {
                                2 -> parts[0] to parts[1]
                                1 -> parts[0] to ""
                                else -> return@let null
                            }
                        } ?: return@firstNotNullOfOrNull null
                        if (key.equals("lightning", ignoreCase = true) && value.isNotEmpty()) {
                            decodeUrlComponent(value)
                        } else {
                            null
                        }
                    } ?: trimmed
                }
            }

            else -> trimmed
        }
    }

    private fun decodeUrlComponent(value: String): String {
        if (value.none { it == '%' || it == '+' }) return value
        val sb = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            when (val ch = value[index]) {
                '+' -> {
                    sb.append(' ')
                    index += 1
                }

                '%' -> {
                    if (index + 2 >= value.length) {
                        return value
                    }
                    val hex = value.substring(index + 1, index + 3)
                    val decoded = hex.toIntOrNull(16) ?: return value
                    sb.append(decoded.toChar())
                    index += 3
                }

                else -> {
                    sb.append(ch)
                    index += 1
                }
            }
        }
        return sb.toString()
    }

    companion object {
        private const val SIGNATURE_LENGTH_WORDS = 104
        private const val TIMESTAMP_WORD_COUNT = 7

        private const val TYPE_DESCRIPTION = 13
        private const val TYPE_DESCRIPTION_HASH = 23

        private const val MSATS_PER_BTC = 100_000_000_000L
        private const val MSATS_PER_MILLI_BITCOIN = 100_000_000L
        private const val MSATS_PER_MICRO_BITCOIN = 100_000L
        private const val MSATS_PER_NANO_BITCOIN = 100L
    }
}

data class Bolt11InvoiceSummary(
    val paymentRequest: String,
    val amountMsats: Long?,
    val memo: Bolt11Memo,
)

sealed class Bolt11Memo {
    data class Text(val value: String) : Bolt11Memo()
    data class HashOnly(val hash: ByteArray) : Bolt11Memo()
    data object None : Bolt11Memo()
}

sealed class Bolt11ParseResult {
    data class Success(val invoice: Bolt11InvoiceSummary) : Bolt11ParseResult()
    data class Failure(val reason: String) : Bolt11ParseResult()
}
