package xyz.lilsus.lasr.integration.nwc

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class NwcCredentials(val connectionUri: String, val alias: String)

internal class NwcCredentialStore(private val settings: Settings, private val json: Json = Json) {
    fun read(): NwcCredentials? {
        val encoded = settings.getStringOrNull(CREDENTIALS_KEY) ?: return null
        return runCatching {
            json.decodeFromString<StoredNwcCredentials>(encoded).toCredentials()
        }.getOrNull()
    }

    fun save(credentials: NwcCredentials) {
        require(credentials.connectionUri.isNotBlank()) {
            "NWC connection URI cannot be blank"
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
private data class StoredNwcCredentials(val connectionUri: String, val alias: String)

private fun NwcCredentials.toStored(): StoredNwcCredentials = StoredNwcCredentials(
    connectionUri = connectionUri,
    alias = alias
)

private fun StoredNwcCredentials.toCredentials(): NwcCredentials? {
    if (connectionUri.isBlank() || alias.isBlank()) return null
    return NwcCredentials(
        connectionUri = connectionUri,
        alias = alias
    )
}

private const val CREDENTIALS_KEY = "credentials"
