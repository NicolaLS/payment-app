package xyz.lilsus.raylsuite.core.payment

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals

class OrderedPaymentInputParserTest {
    @Test
    fun firstSupportedMatchWins() = runImmediateSuspend {
        val visited = mutableListOf<String>()
        val parser =
            OrderedPaymentInputParser<String, String>(
                supportedParsers =
                    listOf(
                        parser("bolt11", visited, PaymentInputParseAttempt.Parsed("bolt")),
                        parser("spark", visited, PaymentInputParseAttempt.Parsed("spark"))
                    )
            )

        assertEquals(PaymentInputParseAttempt.Parsed("bolt"), parser.parse("value"))
        assertEquals(listOf("bolt11"), visited)
    }

    @Test
    fun redirectRestartsAtHighestPriority() = runImmediateSuspend {
        val visited = mutableListOf<String>()
        val parser =
            OrderedPaymentInputParser<String, String>(
                supportedParsers =
                    listOf(
                        PaymentInputParser { value ->
                            visited += "invoice:$value"
                            if (value == "invoice") {
                                PaymentInputParseAttempt.Parsed("paid")
                            } else {
                                PaymentInputParseAttempt.NoMatch
                            }
                        },
                        PaymentInputParser { value ->
                            visited += "envelope:$value"
                            if (value == "bitcoin") {
                                PaymentInputParseAttempt.Reparse("invoice")
                            } else {
                                PaymentInputParseAttempt.NoMatch
                            }
                        }
                    )
            )

        assertEquals(PaymentInputParseAttempt.Parsed("paid"), parser.parse("bitcoin"))
        assertEquals(
            listOf("invoice:bitcoin", "envelope:bitcoin", "invoice:invoice"),
            visited
        )
    }

    @Test
    fun unsupportedRecognizersRunOnlyAfterSupportedParsers() = runImmediateSuspend {
        val visited = mutableListOf<String>()
        val parser =
            OrderedPaymentInputParser<String, String>(
                supportedParsers =
                    listOf(parser("supported", visited, PaymentInputParseAttempt.NoMatch)),
                unsupportedParsers =
                    listOf(
                        parser(
                            "unsupported",
                            visited,
                            PaymentInputParseAttempt.Rejected("known")
                        )
                    )
            )

        assertEquals(PaymentInputParseAttempt.Rejected("known"), parser.parse("value"))
        assertEquals(listOf("supported", "unsupported"), visited)
    }

    private fun parser(
        name: String,
        visited: MutableList<String>,
        result: PaymentInputParseAttempt<String, String>
    ): PaymentInputParser<String, String> = PaymentInputParser {
        visited += name
        result
    }
}

private fun <T> runImmediateSuspend(block: suspend () -> T): T {
    var outcome: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        }
    )
    return checkNotNull(outcome).getOrThrow()
}
