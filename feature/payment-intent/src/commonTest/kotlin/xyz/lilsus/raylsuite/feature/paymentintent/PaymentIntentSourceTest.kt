package xyz.lilsus.raylsuite.feature.paymentintent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import xyz.lilsus.raylsuite.core.model.LightningAddress

class PaymentIntentSourceTest {
    @Test
    fun `lnurl source normalizes scheme and authority`() {
        assertEquals(
            lnurlPaymentIntentSourceKey("HTTPS://PAY.EXAMPLE.COM/lnurl?tag=payRequest"),
            lnurlPaymentIntentSourceKey("https://pay.example.com/lnurl?tag=payRequest")
        )
    }

    @Test
    fun `lnurl source preserves case-sensitive path and query`() {
        assertNotEquals(
            lnurlPaymentIntentSourceKey("https://pay.example.com/Pay?item=A"),
            lnurlPaymentIntentSourceKey("https://pay.example.com/pay?item=a")
        )
    }

    @Test
    fun `lightning address source follows address case-insensitive identity`() {
        assertEquals(
            lightningAddressPaymentIntentSourceKey(
                LightningAddress("Alice", "Example.COM", "Shop")
            ),
            lightningAddressPaymentIntentSourceKey(
                LightningAddress("alice", "example.com", "shop")
            )
        )
    }
}
