package xyz.lilsus.raylsuite.feature.paymenthub

import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata
import xyz.lilsus.raylsuite.core.network.createHttpClient
import xyz.lilsus.raylsuite.core.settings.SecureStringStore
import xyz.lilsus.raylsuite.integration.hub.KtorHubWidgetCatalogClient

/** The Hub surface owns this transport; no wallet credentials enter it. */
internal class HubRemoteSession(metadata: HubClientMetadata, secure: SecureStringStore) {
    private val http = createHttpClient()
    val catalog = KtorHubWidgetCatalogClient(HubBackendConfiguration.baseUrl, metadata, http)
    val orderStore = HubServiceOrderStore(secure, HubBackendConfiguration.baseUrl)
    fun close() = http.close()
}
