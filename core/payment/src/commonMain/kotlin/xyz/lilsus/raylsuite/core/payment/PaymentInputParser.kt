package xyz.lilsus.raylsuite.core.payment

/**
 * One independently reusable step in payment-input parsing.
 *
 * Parsers are tried in list order. A parser must return [PaymentInputParseAttempt.NoMatch]
 * without doing expensive work when the input clearly does not belong to it.
 */
fun interface PaymentInputParser<T, F> {
    suspend fun parse(value: String): PaymentInputParseAttempt<T, F>
}

sealed interface PaymentInputParseAttempt<out T, out F> {
    data class Parsed<T>(val value: T) : PaymentInputParseAttempt<T, Nothing>

    data class Reparse(val value: String) : PaymentInputParseAttempt<Nothing, Nothing>

    data class Rejected<F>(val reason: F) : PaymentInputParseAttempt<Nothing, F>

    data object NoMatch : PaymentInputParseAttempt<Nothing, Nothing>
}

/**
 * Composes supported parsers and known-unsupported recognizers without a mutable registry.
 *
 * Priority is explicit: earlier entries win. App-owned instant-payment parsers can therefore
 * be inserted immediately after the shared BOLT11 parser. Unsupported recognizers are kept in
 * a separate final phase and can never shadow a payable input.
 */
class OrderedPaymentInputParser<T, F>(
    private val supportedParsers: List<PaymentInputParser<T, F>>,
    private val unsupportedParsers: List<PaymentInputParser<T, F>> = emptyList()
) : PaymentInputParser<T, F> {
    override suspend fun parse(value: String): PaymentInputParseAttempt<T, F> =
        parse(value, reparsedValues = emptySet())

    private suspend fun parse(
        value: String,
        reparsedValues: Set<String>
    ): PaymentInputParseAttempt<T, F> {
        val supported = parseWith(supportedParsers, value)
        if (supported !is PaymentInputParseAttempt.NoMatch) {
            return followReparse(supported, reparsedValues)
        }

        val unsupported = parseWith(unsupportedParsers, value)
        return followReparse(unsupported, reparsedValues)
    }

    private suspend fun parseWith(
        parsers: List<PaymentInputParser<T, F>>,
        value: String
    ): PaymentInputParseAttempt<T, F> {
        parsers.forEach { parser ->
            when (val attempt = parser.parse(value)) {
                PaymentInputParseAttempt.NoMatch -> Unit
                else -> return attempt
            }
        }
        return PaymentInputParseAttempt.NoMatch
    }

    private suspend fun followReparse(
        attempt: PaymentInputParseAttempt<T, F>,
        reparsedValues: Set<String>
    ): PaymentInputParseAttempt<T, F> {
        if (attempt !is PaymentInputParseAttempt.Reparse) return attempt

        val redirected = attempt.value.trim()
        if (redirected.isEmpty() || redirected in reparsedValues) {
            return PaymentInputParseAttempt.NoMatch
        }
        return parse(redirected, reparsedValues + redirected)
    }
}
