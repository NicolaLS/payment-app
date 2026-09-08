package xyz.lilsus.raylsuite.feature.paymenthub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.hubapi.HubServiceMoney
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrder
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrderRequest
import xyz.lilsus.raylsuite.core.settings.SecureStringStore

/** A recovery credential is a secret, separate from contacts and widget layout. */
class HubServiceOrderStore(private val secure: SecureStringStore, endpoint: String) {
    private val json = Json { ignoreUnknownKeys = true }
    private val key = "service-order:" + endpoint.trimEnd('/').encodeToByteArray()
        .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

    internal fun load(): StoredHubServiceOrder? = secure.getStringOrNull(key)?.let {
        json.decodeFromString<StoredHubServiceOrder>(it)
    }

    internal fun save(order: StoredHubServiceOrder) =
        secure.putString(key, json.encodeToString(order))
    internal fun remove() = secure.remove(key)
}

@Serializable
internal data class StoredHubServiceOrder(
    val id: String,
    val token: String,
    val widgetId: String,
    val title: String,
    val request: HubServiceOrderRequest,
    val latest: HubServiceOrder? = null,
    val expectedAmount: HubServiceMoney? = null
)

internal data class HubOrderCredentials(val id: String, val token: String)

internal expect fun newHubOrderCredentials(): HubOrderCredentials
