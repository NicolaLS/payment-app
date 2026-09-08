package xyz.lilsus.raylsuite.feature.paymenthub

import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata
import xyz.lilsus.raylsuite.core.network.createHttpClient
import xyz.lilsus.raylsuite.integration.hub.KtorHubWidgetCatalogClient

/** The Hub surface owns this transport; no wallet credentials enter it. */
internal class HubRemoteSession(metadata: HubClientMetadata) {
    private val http = createHttpClient()
    val catalog = KtorHubWidgetCatalogClient(HubBackendConfiguration.baseUrl, metadata, http)
    fun close() = http.close()
}
