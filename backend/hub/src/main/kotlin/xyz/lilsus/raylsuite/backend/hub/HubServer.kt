package xyz.lilsus.raylsuite.backend.hub

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.hubapi.HubApiError
import xyz.lilsus.raylsuite.core.hubapi.HubRequestHeaders
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetCatalog
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetProtocol

/** Local development entry point. No supplier integrations or account database are installed. */
fun main() {
    val host = System.getenv("HUB_HOST") ?: "127.0.0.1"
    val port = (System.getenv("HUB_PORT") ?: "8080").toInt()
    require(port in 1..65_535) { "HUB_PORT must be between 1 and 65535" }
    embeddedServer(CIO, host = host, port = port) { hubModule() }.start(wait = true)
}

fun Application.hubModule() {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    routing {
        get(HubWidgetProtocol.CATALOG_PATH) {
            if (!call.requireClientMetadata()) return@get
            call.response.header(HttpHeaders.CacheControl, "no-store")
            call.respond(HubWidgetCatalog())
        }
        post("${HubWidgetProtocol.CATALOG_PATH}/{widgetId}/content") {
            if (!call.requireClientMetadata()) return@post
            // The production catalogue is empty. Examples in docs are not live services.
            call.respond(HttpStatusCode.NotFound, HubApiError("widget_not_found"))
        }
    }
}

private suspend fun ApplicationCall.requireClientMetadata(): Boolean {
    val required = listOf(
        HubRequestHeaders.APP,
        HubRequestHeaders.VERSION,
        HubRequestHeaders.BUILD,
        HubRequestHeaders.PLATFORM,
        HubRequestHeaders.CONTRACTS,
        HttpHeaders.AcceptLanguage
    )
    val invalid = required.any { name ->
        val value = request.headers[name]
        value == null || value.length > 512 || value.any(Char::isISOControl) ||
            (name != HubRequestHeaders.CONTRACTS && value.isBlank())
    }
    if (invalid) respond(HttpStatusCode.BadRequest, HubApiError("client_metadata_required"))
    return !invalid
}
