package xyz.lilsus.papp

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.lilsus.papp.navigation.PaymentDeepLinkSource

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

    @Test
    fun classifiesCameraSourceOnlyForBitcoinE2eLinks() {
        val cases = listOf(
            SourceCase(
                uri = "bitcoin:?lightning=lnbc1amountinvoice&source=camera",
                e2eHooksAllowed = false,
                expected = PaymentDeepLinkSource.DeepLink
            ),
            SourceCase(
                uri = "lightning:lnbc1amountinvoice?source=camera",
                e2eHooksAllowed = true,
                expected = PaymentDeepLinkSource.DeepLink
            ),
            SourceCase(
                uri = "bitcoin:?lightning=lnbc1amountinvoice&source=camera",
                e2eHooksAllowed = true,
                expected = PaymentDeepLinkSource.Camera
            )
        )

        cases.forEach { case ->
            assertEquals(
                case.expected,
                paymentDeepLinkSource(
                    uri = case.uri,
                    e2eHooksAllowed = case.e2eHooksAllowed
                )
            )
        }
    }

    private data class SourceCase(val uri: String, val e2eHooksAllowed: Boolean, val expected: PaymentDeepLinkSource)
}
