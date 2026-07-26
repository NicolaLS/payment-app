package xyz.lilsus.papp

import kotlin.test.Test
import kotlin.test.assertEquals

class AppDeepLinkTest {
    @Test
    fun extractsPaymentInputFromSupportedLinks() {
        val cases = listOf(
            "lightning:pay@blink.sv" to "pay@blink.sv",
            "lnurl:lnurlp://example.com/lnurl" to "lnurlp://example.com/lnurl",
            "bitcoin:?lightning=lnbc1amountinvoice" to "lnbc1amountinvoice",
            "bitcoin:bc1qexample?amount=0.001&lightning=lnbc1amountinvoice" to
                "lnbc1amountinvoice",
            "bitcoin:bc1qexample?amount=0.001" to null
        )

        cases.forEach { (uri, expected) ->
            assertEquals(expected, paymentInputFromDeepLink(uri))
        }
    }
}
