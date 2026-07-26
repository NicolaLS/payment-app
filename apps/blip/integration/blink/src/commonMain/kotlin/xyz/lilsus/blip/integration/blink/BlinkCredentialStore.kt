package xyz.lilsus.blip.integration.blink

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class BlinkCredentials(
    val apiKey: String,
    val defaultWalletId: String,
    val alias: String
)

internal class BlinkCredentialStore(private val settings: Settings, private val json: Json = Json) {
    fun read(): BlinkCredentials? {
        val encoded = settings.getStringOrNull(CREDENTIALS_KEY) ?: return null
        return runCatching {
            json.decodeFromString<StoredBlinkCredentials>(encoded).toCredentials()
        }.getOrNull()
    }

    fun save(credentials: BlinkCredentials) {
        require(credentials.apiKey.isNotBlank()) { "Blink API key cannot be blank" }
        require(credentials.defaultWalletId.isNotBlank()) {
            "Blink wallet ID cannot be blank"
        }
        require(credentials.alias.isNotBlank()) { "Wallet alias cannot be blank" }

        settings.putString(
            CREDENTIALS_KEY,
            json.encodeToString(credentials.toStored())
        )
    }

    fun clear() {
        settings.remove(CREDENTIALS_KEY)
    }
}

@Serializable
private data class StoredBlinkCredentials(
    val apiKey: String,
    val defaultWalletId: String,
    val alias: String
)

private fun BlinkCredentials.toStored(): StoredBlinkCredentials = StoredBlinkCredentials(
    apiKey = apiKey,
    defaultWalletId = defaultWalletId,
    alias = alias
)

private fun StoredBlinkCredentials.toCredentials(): BlinkCredentials? {
    if (apiKey.isBlank() || defaultWalletId.isBlank() || alias.isBlank()) return null
    return BlinkCredentials(
        apiKey = apiKey,
        defaultWalletId = defaultWalletId,
        alias = alias
    )
}

private const val CREDENTIALS_KEY = "credentials"
