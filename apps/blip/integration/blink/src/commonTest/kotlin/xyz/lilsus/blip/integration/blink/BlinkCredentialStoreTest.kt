package xyz.lilsus.blip.integration.blink

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlinkCredentialStoreTest {
    @Test
    fun storesAndClearsOneCredentialSet() {
        val store = BlinkCredentialStore(MapSettings())
        val credentials =
            BlinkCredentials(
                apiKey = "blink_test",
                defaultWalletId = "wallet-id",
                alias = "Shop"
            )

        store.save(credentials)
        assertEquals(credentials, store.read())

        store.clear()
        assertNull(store.read())
    }
}
