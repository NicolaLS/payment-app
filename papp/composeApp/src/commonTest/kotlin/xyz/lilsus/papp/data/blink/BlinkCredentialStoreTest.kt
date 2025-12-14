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
    fun storeApiKeyPersistsKey() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        store.storeApiKey("wallet-123", "blink_api_key_secret")

        assertEquals("blink_api_key_secret", store.getApiKey("wallet-123"))
    }

    @Test
    fun getApiKeyReturnsNullForUnknownWallet() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        val result = store.getApiKey("unknown-wallet")

        assertNull(result)
    }

    @Test
    fun removeApiKeyDeletesKey() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)
        store.storeApiKey("wallet-123", "blink_api_key_secret")

        store.removeApiKey("wallet-123")

        assertNull(store.getApiKey("wallet-123"))
    }

    @Test
    fun hasApiKeyReturnsTrueWhenKeyExists() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)
        store.storeApiKey("wallet-123", "blink_api_key_secret")

        assertTrue(store.hasApiKey("wallet-123"))
    }

    @Test
    fun hasApiKeyReturnsFalseWhenKeyDoesNotExist() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        assertFalse(store.hasApiKey("unknown-wallet"))
    }

    @Test
    fun multipleWalletsHaveIndependentKeys() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        store.storeApiKey("wallet-1", "key-1")
        store.storeApiKey("wallet-2", "key-2")

        assertEquals("key-1", store.getApiKey("wallet-1"))
        assertEquals("key-2", store.getApiKey("wallet-2"))
    }

    @Test
    fun removeApiKeyDoesNotAffectOtherWallets() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)
        store.storeApiKey("wallet-1", "key-1")
        store.storeApiKey("wallet-2", "key-2")

        store.removeApiKey("wallet-1")

        assertNull(store.getApiKey("wallet-1"))
        assertEquals("key-2", store.getApiKey("wallet-2"))
    }

    @Test
    fun storeApiKeyOverwritesExistingKey() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)
        store.storeApiKey("wallet-123", "old-key")

        store.storeApiKey("wallet-123", "new-key")

        assertEquals("new-key", store.getApiKey("wallet-123"))
    }

    @Test
    fun getApiKeyReturnsNullForBlankWalletId() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        assertNull(store.getApiKey(""))
        assertNull(store.getApiKey("   "))
    }

    @Test
    fun hasApiKeyReturnsFalseForBlankWalletId() {
        val settings = MapSettings()
        val store = BlinkCredentialStore(settings)

        assertFalse(store.hasApiKey(""))
        assertFalse(store.hasApiKey("   "))
    }
}
