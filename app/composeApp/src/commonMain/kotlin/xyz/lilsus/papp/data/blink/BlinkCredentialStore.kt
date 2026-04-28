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
     * Stores the Blink default wallet ID for the given wallet ID.
     */
    fun storeDefaultWalletId(walletId: String, defaultWalletId: String) {
        require(walletId.isNotBlank()) { "Wallet ID must not be blank" }
        require(defaultWalletId.isNotBlank()) { "Default wallet ID must not be blank" }
        secureSettings.putString(defaultWalletKeyFor(walletId), defaultWalletId)
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
     * Retrieves the default Blink wallet ID for the given wallet ID.
     * @return The default wallet ID, or null if not found.
     */
    fun getDefaultWalletId(walletId: String): String? {
        if (walletId.isBlank()) return null
        return secureSettings.getStringOrNull(defaultWalletKeyFor(walletId))
    }

    /**
     * Removes the API key for the given wallet ID.
     */
    fun removeApiKey(walletId: String) {
        if (walletId.isBlank()) return
        secureSettings.remove(keyFor(walletId))
    }

    /**
     * Removes the default wallet ID for the given wallet ID.
     */
    fun removeDefaultWalletId(walletId: String) {
        if (walletId.isBlank()) return
        secureSettings.remove(defaultWalletKeyFor(walletId))
    }

    /**
     * Checks if an API key exists for the given wallet ID.
     */
    fun hasApiKey(walletId: String): Boolean {
        if (walletId.isBlank()) return false
        return secureSettings.getStringOrNull(keyFor(walletId)) != null
    }

    private fun keyFor(walletId: String): String = "$KEY_PREFIX$walletId"
    private fun defaultWalletKeyFor(walletId: String): String = "$DEFAULT_WALLET_PREFIX$walletId"

    companion object {
        private const val KEY_PREFIX = "blink.apikey."
        private const val DEFAULT_WALLET_PREFIX = "blink.defaultWallet."
    }
}
