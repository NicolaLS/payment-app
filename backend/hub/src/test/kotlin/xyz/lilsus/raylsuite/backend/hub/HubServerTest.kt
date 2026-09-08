package xyz.lilsus.raylsuite.backend.hub

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.hubapi.HubRequestHeaders
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetCatalog
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetProtocol

class HubServerTest {
    @Test
    fun emptyCatalogueRequiresMetadataAndUnknownContentIsUnavailable() = testApplication {
        application { hubModule() }
        assertEquals(HttpStatusCode.BadRequest, client.get(HubWidgetProtocol.CATALOG_PATH).status)
        val response = client.get(HubWidgetProtocol.CATALOG_PATH) {
            header(HubRequestHeaders.APP, "rayl")
            header(HubRequestHeaders.VERSION, "1.0.0")
            header(HubRequestHeaders.BUILD, "1")
            header(HubRequestHeaders.PLATFORM, "ios")
            header(HubRequestHeaders.CONTRACTS, "metric/v1")
            header(HttpHeaders.AcceptLanguage, "en")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            HubWidgetCatalog(),
            Json.decodeFromString<HubWidgetCatalog>(response.bodyAsText())
        )
        val content = client.post("${HubWidgetProtocol.CATALOG_PATH}/unavailable/content") {
            header(HubRequestHeaders.APP, "rayl")
            header(HubRequestHeaders.VERSION, "1.0.0")
            header(HubRequestHeaders.BUILD, "1")
            header(HubRequestHeaders.PLATFORM, "android")
            header(HubRequestHeaders.CONTRACTS, "metric/v1")
            header(HttpHeaders.AcceptLanguage, "de")
        }
        assertEquals(HttpStatusCode.NotFound, content.status)
    }
}
