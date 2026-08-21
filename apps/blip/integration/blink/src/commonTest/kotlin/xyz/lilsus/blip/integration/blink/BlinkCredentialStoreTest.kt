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
                defaultWalletId = "wallet-id"
            )

        store.save(credentials)
        assertEquals(credentials, store.read())

        store.clear()
        assertNull(store.read())
    }

    @Test
    fun ignoresUnknownCredentialFields() {
        val settings = MapSettings()
        settings.putString(
            "credentials",
            """{"apiKey":"blink_test","defaultWalletId":"wallet-id","future":true}"""
        )

        assertEquals(
            BlinkCredentials(apiKey = "blink_test", defaultWalletId = "wallet-id"),
            BlinkCredentialStore(settings).read()
        )
    }
}
