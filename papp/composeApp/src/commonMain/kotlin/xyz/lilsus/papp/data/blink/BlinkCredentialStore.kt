package xyz.lilsus.papp.data.blink

import com.russhwolf.settings.Settings

/**
 * Secure storage for Blink API keys.
 * Uses platform-appropriate secure storage (Android Keystore / iOS Keychain).
 *
 * API keys are stored with wallet ID as the key prefix to support multiple Blink wallets.
 */
class BlinkCredentialStore(private val secureSettings: Settings) {

    /**
     * Stores an API key for the given wallet ID.
     * The key is encrypted using platform-secure storage.
     */
    fun storeApiKey(walletId: String, apiKey: String) {
        require(walletId.isNotBlank()) { "Wallet ID must not be blank" }
        require(apiKey.isNotBlank()) { "API key must not be blank" }
        secureSettings.putString(keyFor(walletId), apiKey)
    }

    /**
     * Retrieves the API key for the given wallet ID.
     * @return The API key, or null if not found.
     */
    fun getApiKey(walletId: String): String? {
        if (walletId.isBlank()) return null
        return secureSettings.getStringOrNull(keyFor(walletId))
    }

    /**
     * Removes the API key for the given wallet ID.
     */
    fun removeApiKey(walletId: String) {
        if (walletId.isBlank()) return
        secureSettings.remove(keyFor(walletId))
    }

    /**
     * Checks if an API key exists for the given wallet ID.
     */
    fun hasApiKey(walletId: String): Boolean {
        if (walletId.isBlank()) return false
        return secureSettings.getStringOrNull(keyFor(walletId)) != null
    }

    private fun keyFor(walletId: String): String = "$KEY_PREFIX$walletId"

    companion object {
        private const val KEY_PREFIX = "blink.apikey."
    }
}
