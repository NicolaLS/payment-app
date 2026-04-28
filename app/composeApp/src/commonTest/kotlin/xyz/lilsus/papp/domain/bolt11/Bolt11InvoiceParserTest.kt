package xyz.lilsus.papp.domain.bolt11

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class Bolt11InvoiceParserTest {

    private val parser = Bolt11InvoiceParser()

    @Test
    fun parsesAmountAndMemo() {
        val result = parser.parse(SAMPLE_WITH_AMOUNT_AND_MEMO)
        val invoice = result.expectSuccess()

        assertEquals(SAMPLE_WITH_AMOUNT_AND_MEMO, invoice.paymentRequest)
        assertEquals(250_000_000L, invoice.amountMsats)
        val memo = invoice.memo
        assertTrue(memo is Bolt11Memo.Text)
        assertEquals("1 cup coffee", memo.value)
    }

    @Test
    fun parsesInvoicesWithoutAmount() {
        val result = parser.parse(SAMPLE_WITHOUT_AMOUNT)
        val invoice = result.expectSuccess()

        assertEquals(SAMPLE_WITHOUT_AMOUNT, invoice.paymentRequest)
        assertEquals(null, invoice.amountMsats)
        val memo = invoice.memo
        assertTrue(memo is Bolt11Memo.Text)
        assertEquals("Please consider supporting this project", memo.value)
    }

    @Test
    fun detectsHashedDescription() {
        val result = parser.parse(SAMPLE_WITH_HASHED_DESCRIPTION)
        val invoice = result.expectSuccess()

        assertEquals(SAMPLE_WITH_HASHED_DESCRIPTION, invoice.paymentRequest)
        assertEquals(2_000_000_000L, invoice.amountMsats)
        val memo = invoice.memo
        assertTrue(memo is Bolt11Memo.HashOnly)
        assertEquals(32, memo.hash.size)
    }

    @Test
    fun failsOnInvalidMultiplier() {
        val result = parser.parse(SAMPLE_WITH_INVALID_MULTIPLIER)
        assertTrue(result is Bolt11ParseResult.Failure)
    }

    @Test
    fun parsesInvoiceFromBitcoinUri() {
        val uri = "bitcoin:bc1qexample?amount=0.001&foo=bar&lightning=$SAMPLE_WITH_AMOUNT_AND_MEMO"
        val result = parser.parse(uri)
        val invoice = result.expectSuccess()

        assertEquals(SAMPLE_WITH_AMOUNT_AND_MEMO, invoice.paymentRequest)
        assertEquals(250_000_000L, invoice.amountMsats)
        assertTrue(invoice.memo is Bolt11Memo.Text)
    }

    @Test
    fun parsesUppercaseInvoice() {
        val uppercase = SAMPLE_WITH_AMOUNT_AND_MEMO.uppercase()
        val result = parser.parse(uppercase)
        val invoice = result.expectSuccess()

        assertEquals(SAMPLE_WITH_AMOUNT_AND_MEMO, invoice.paymentRequest)
        assertEquals(250_000_000L, invoice.amountMsats)
        assertTrue(invoice.memo is Bolt11Memo.Text)
    }

    @Test
    fun parsesBitcoinUriWithEncodedLightning() {
        val encoded = "bitcoin:bc1qexample?lightning=${encode(SAMPLE_WITHOUT_AMOUNT)}"
        val result = parser.parse(encoded)
        val invoice = result.expectSuccess()

        assertEquals(SAMPLE_WITHOUT_AMOUNT, invoice.paymentRequest)
        assertEquals(null, invoice.amountMsats)
        assertTrue(invoice.memo is Bolt11Memo.Text)
    }

    @Test
    fun parsesInvoiceAfterBitcoinPrefixWithoutQuery() {
        val uri = "bitcoin:$SAMPLE_WITH_AMOUNT_AND_MEMO"
        val result = parser.parse(uri)
        val invoice = result.expectSuccess()

        assertEquals(SAMPLE_WITH_AMOUNT_AND_MEMO, invoice.paymentRequest)
        assertEquals(250_000_000L, invoice.amountMsats)
    }

    private fun Bolt11ParseResult.expectSuccess(): Bolt11InvoiceSummary = when (this) {
        is Bolt11ParseResult.Success -> this.invoice
        is Bolt11ParseResult.Failure -> fail("Expected success but got failure: $reason")
    }

    private fun encode(value: String): String = buildString(value.length * 2) {
        value.forEach { ch ->
            when (ch) {
                ' ' -> append('+')

                else -> {
                    append('%')
                    append(ch.code.toString(16).padStart(2, '0'))
                }
            }
        }
    }

    companion object {
        private const val SAMPLE_WITH_AMOUNT_AND_MEMO =
            "lnbc2500u1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdq5xysxxatsyp3k7enxv4jsxqzpu9qrsgquk0rl77nj30yxdy8j9vdx85fkpmdla2087ne0xh8nhedh8w27kyke0lp53ut353s06fv3qfegext0eh0ymjpf39tuven09sam30g4vgpfna3rh"

        private const val SAMPLE_WITHOUT_AMOUNT =
            "lnbc1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdpl2pkx2ctnv5sxxmmwwd5kgetjypeh2ursdae8g6twvus8g6rfwvs8qun0dfjkxaq9qrsgq357wnc5r2ueh7ck6q93dj32dlqnls087fxdwk8qakdyafkq3yap9us6v52vjjsrvywa6rt52cm9r9zqt8r2t7mlcwspyetp5h2tztugp9lfyql"

        private const val SAMPLE_WITH_HASHED_DESCRIPTION =
            "lnbc20m1pvjluezsp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygspp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqs9qrsgq7ea976txfraylvgzuxs8kgcw23ezlrszfnh8r6qtfpr6cxga50aj6txm9rxrydzd06dfeawfk6swupvz4erwnyutnjq7x39ymw6j38gp7ynn44"

        private const val SAMPLE_WITH_INVALID_MULTIPLIER =
            "lnbc2500x1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdq5xysxxatsyp3k7enxv4jsxqzpusp5zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zyg3zygs9qrsgqrrzc4cvfue4zp3hggxp47ag7xnrlr8vgcmkjxk3j5jqethnumgkpqp23z9jclu3v0a7e0aruz366e9wqdykw6dxhdzcjjhldxq0w6wgqcnu43j"
    }
}
