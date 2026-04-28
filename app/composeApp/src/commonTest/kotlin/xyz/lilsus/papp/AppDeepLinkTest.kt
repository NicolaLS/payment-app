package xyz.lilsus.papp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import xyz.lilsus.papp.navigation.PaymentDeepLinkSource

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
            paymentInputFromDeepLink(
                "bitcoin:bc1qexample?amount=0.001&lightning=lnbc1amountinvoice"
            )
        )
    }

    @Test
    fun ignoresBitcoinLinksWithoutLightningParameter() {
        assertNull(paymentInputFromDeepLink("bitcoin:bc1qexample?amount=0.001"))
    }

    @Test
    fun treatsCameraSourceAsDeepLinkWhenE2eHooksAreDisabled() {
        assertEquals(
            PaymentDeepLinkSource.DeepLink,
            paymentDeepLinkSource(
                uri = "bitcoin:?lightning=lnbc1amountinvoice&source=camera",
                e2eHooksAllowed = false
            )
        )
    }

    @Test
    fun treatsNonBitcoinCameraSourceAsDeepLinkWhenE2eHooksAreEnabled() {
        assertEquals(
            PaymentDeepLinkSource.DeepLink,
            paymentDeepLinkSource(
                uri = "lightning:lnbc1amountinvoice?source=camera",
                e2eHooksAllowed = true
            )
        )
    }

    @Test
    fun extractsBitcoinCameraSourceWhenE2eHooksAreEnabled() {
        assertEquals(
            PaymentDeepLinkSource.Camera,
            paymentDeepLinkSource(
                uri = "bitcoin:?lightning=lnbc1amountinvoice&source=camera",
                e2eHooksAllowed = true
            )
        )
    }
}
