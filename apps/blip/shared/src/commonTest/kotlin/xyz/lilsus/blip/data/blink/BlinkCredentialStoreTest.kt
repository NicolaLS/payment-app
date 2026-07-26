package xyz.lilsus.blip.data.blink

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlinkCredentialStoreTest {

    @Test
    fun storesOverwritesAndClearsTheConnectionCredentials() {
        val store = BlinkCredentialStore(MapSettings())

        store.storeApiKey("blink_api_key_secret")
        store.storeDefaultWalletId("wallet-id")
        assertEquals("blink_api_key_secret", store.getApiKey())
        assertEquals("wallet-id", store.getDefaultWalletId())
        assertTrue(store.hasApiKey())

        store.storeApiKey("new-key")
        store.clear()

        assertNull(store.getApiKey())
        assertNull(store.getDefaultWalletId())
        assertFalse(store.hasApiKey())
    }

    @Test
    fun migratesOnlyTheRetainedLegacyWalletCredentials() {
        val settings = MapSettings(
            "blink.apikey.retained" to "retained-key",
            "blink.defaultWallet.retained" to "retained-wallet-id",
            "blink.apikey.discarded" to "discarded-key",
            "blink.defaultWallet.discarded" to "discarded-wallet-id"
        )
        val store = BlinkCredentialStore(settings)

        store.migrateLegacyWallets(
            retainedWalletId = "retained",
            discardedWalletIds = listOf("discarded")
        )

        assertEquals("retained-key", store.getApiKey())
        assertEquals("retained-wallet-id", store.getDefaultWalletId())
        assertNull(settings.getStringOrNull("blink.apikey.retained"))
        assertNull(settings.getStringOrNull("blink.defaultWallet.retained"))
        assertNull(settings.getStringOrNull("blink.apikey.discarded"))
        assertNull(settings.getStringOrNull("blink.defaultWallet.discarded"))
    }
}
