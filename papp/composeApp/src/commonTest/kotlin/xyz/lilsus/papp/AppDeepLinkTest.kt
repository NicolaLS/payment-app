package xyz.lilsus.papp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppDeepLinkTest {
    @Test
    fun extractsLightningPaymentInput() {
        assertEquals("pay@blink.sv", paymentInputFromDeepLink("lightning:pay@blink.sv"))
    }

    @Test
    fun extractsLnurlPaymentInput() {
        assertEquals(
            "lnurlp://example.com/lnurl",
            paymentInputFromDeepLink("lnurl:lnurlp://example.com/lnurl")
        )
    }

    @Test
    fun extractsBitcoinLightningParameter() {
        assertEquals(
            "lnbc1amountinvoice",
            paymentInputFromDeepLink("bitcoin:?lightning=lnbc1amountinvoice")
        )
    }

    @Test
    fun extractsBitcoinLightningParameterWithFallbackAddress() {
        assertEquals(
            "lnbc1amountinvoice",
            paymentInputFromDeepLink("bitcoin:bc1qexample?amount=0.001&lightning=lnbc1amountinvoice")
        )
    }

    @Test
    fun ignoresBitcoinLinksWithoutLightningParameter() {
        assertNull(paymentInputFromDeepLink("bitcoin:bc1qexample?amount=0.001"))
    }
}
