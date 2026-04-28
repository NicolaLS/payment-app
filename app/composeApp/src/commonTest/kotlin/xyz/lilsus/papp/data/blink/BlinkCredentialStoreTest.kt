package xyz.lilsus.papp.data.blink

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for BlinkCredentialStore.
 * Verifies secure storage operations for Blink API keys.
 */
class BlinkCredentialStoreTest {

    @Test
    fun apiKeyCanBeStoredOverwrittenAndRemoved() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        store.storeApiKey("wallet-123", "blink_api_key_secret")
        assertEquals("blink_api_key_secret", store.getApiKey("wallet-123"))
        assertTrue(store.hasApiKey("wallet-123"))

        store.storeApiKey("wallet-123", "new-key")
        assertEquals("new-key", store.getApiKey("wallet-123"))
        store.removeApiKey("wallet-123")

        assertNull(store.getApiKey("wallet-123"))
        assertFalse(store.hasApiKey("wallet-123"))
    }

    @Test
    fun apiKeysAreScopedPerWallet() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        store.storeApiKey("wallet-1", "key-1")
        store.storeApiKey("wallet-2", "key-2")
        store.removeApiKey("wallet-1")

        assertNull(store.getApiKey("wallet-1"))
        assertEquals("key-2", store.getApiKey("wallet-2"))
    }

    @Test
    fun returnsEmptyResultsForMissingOrBlankWalletIds() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        assertNull(store.getApiKey("unknown-wallet"))
        assertFalse(store.hasApiKey(""))
        assertNull(store.getApiKey(""))
        assertFalse(store.hasApiKey("   "))
        assertNull(store.getApiKey("   "))
    }
}
