package xyz.lilsus.raylsuite.core.payment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import xyz.lilsus.raylsuite.core.model.LightningAddress

class DynamicPaymentSourceKeyTest {
    @Test
    fun `lnurl source normalizes scheme and authority`() {
        assertEquals(
            lnurlDynamicPaymentSourceKey("HTTPS://PAY.EXAMPLE.COM/lnurl?tag=payRequest"),
            lnurlDynamicPaymentSourceKey("https://pay.example.com/lnurl?tag=payRequest")
        )
    }

    @Test
    fun `lnurl source preserves case-sensitive path and query`() {
        assertNotEquals(
            lnurlDynamicPaymentSourceKey("https://pay.example.com/Pay?item=A"),
            lnurlDynamicPaymentSourceKey("https://pay.example.com/pay?item=a")
        )
    }

    @Test
    fun `lightning address source follows address case-insensitive identity`() {
        assertEquals(
            lightningAddressDynamicPaymentSourceKey(
                LightningAddress("Alice", "Example.COM", "Shop")
            ),
            lightningAddressDynamicPaymentSourceKey(
                LightningAddress("alice", "example.com", "shop")
            )
        )
    }
}
