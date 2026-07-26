package xyz.lilsus.blip.data.blink

import com.russhwolf.settings.Settings

/** Secure storage for the single optional Blink connection. */
class BlinkCredentialStore(private val secureSettings: Settings) {

    fun storeApiKey(apiKey: String) {
        require(apiKey.isNotBlank()) { "API key must not be blank" }
        secureSettings.putString(KEY_API_KEY, apiKey)
    }

    fun storeDefaultWalletId(defaultWalletId: String) {
        require(defaultWalletId.isNotBlank()) { "Default wallet ID must not be blank" }
        secureSettings.putString(KEY_DEFAULT_WALLET, defaultWalletId)
    }

    fun getApiKey(): String? = secureSettings.getStringOrNull(KEY_API_KEY)

    fun getDefaultWalletId(): String? = secureSettings.getStringOrNull(KEY_DEFAULT_WALLET)

    fun clear() {
        secureSettings.remove(KEY_API_KEY)
        secureSettings.remove(KEY_DEFAULT_WALLET)
    }

    fun hasApiKey(): Boolean = getApiKey() != null

    /**
     * Moves credentials from the former per-wallet key format and removes credentials belonging
     * to wallets discarded by the one-wallet migration.
     */
    fun migrateLegacyWallets(retainedWalletId: String?, discardedWalletIds: List<String>) {
        if (retainedWalletId != null) {
            secureSettings.getStringOrNull(legacyApiKey(retainedWalletId))?.let(::storeApiKey)
            secureSettings.getStringOrNull(legacyDefaultWalletKey(retainedWalletId))
                ?.let(::storeDefaultWalletId)
        }
        (discardedWalletIds + listOfNotNull(retainedWalletId)).forEach { walletId ->
            secureSettings.remove(legacyApiKey(walletId))
            secureSettings.remove(legacyDefaultWalletKey(walletId))
        }
    }

    private fun legacyApiKey(walletId: String): String = "$LEGACY_API_KEY_PREFIX$walletId"

    private fun legacyDefaultWalletKey(walletId: String): String =
        "$LEGACY_DEFAULT_WALLET_PREFIX$walletId"

    private companion object {
        const val KEY_API_KEY = "blink.apikey"
        const val KEY_DEFAULT_WALLET = "blink.defaultWallet"
        const val LEGACY_API_KEY_PREFIX = "blink.apikey."
        const val LEGACY_DEFAULT_WALLET_PREFIX = "blink.defaultWallet."
    }
}
