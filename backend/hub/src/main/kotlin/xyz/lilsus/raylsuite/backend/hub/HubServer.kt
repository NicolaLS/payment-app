package xyz.lilsus.raylsuite.backend.hub

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.utils.io.readAvailable
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import xyz.lilsus.raylsuite.core.hubapi.HubApiError
import xyz.lilsus.raylsuite.core.hubapi.HubRequestHeaders
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrderRequest
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetCatalog
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetContent
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetContentRequest
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetProtocol

/** Local single-process experiment. Credentials and order records never enter the mobile bundle. */
fun main() {
    val host = System.getenv("HUB_HOST") ?: "127.0.0.1"
    val port = (System.getenv("HUB_PORT") ?: "8080").toInt()
    require(port in 1..65_535) { "HUB_PORT must be between 1 and 65535" }
    val apiKey = bitrefillApiKey()
    val http = apiKey?.let {
        HttpClient(OkHttp) {
            followRedirects = false
            engine {
                config {
                    retryOnConnectionFailure(false)
                    followRedirects(false)
                    followSslRedirects(false)
                }
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            // Neither Ktor nor its HTTP engine may replay an invoice POST.
        }
    }
    val suppliers = if (apiKey == null || http == null) {
        System.err.println(
            "Hub: no Bitrefill API key is configured; the remote service catalogue is empty."
        )
        emptyList()
    } else {
        listOf(
            BitrefillSupplier(
                apiKey,
                http,
                topupProductId =
                    System.getenv("BITREFILL_CLARO_TOPUP_PRODUCT_ID") ?: "claro-el-salvador",
                packagesProductId =
                    System.getenv("BITREFILL_CLARO_PACKAGES_PRODUCT_ID")
                        ?: "claro-el-salvador-bundles",
                country = System.getenv("BITREFILL_COUNTRY") ?: "SV",
                callingCode = System.getenv("BITREFILL_CALLING_CODE") ?: "503"
            )
        )
    }
    val journal = ServiceOrderJournal(
        Path.of(System.getenv("HUB_ORDER_DIR") ?: "backend/hub/.runtime/orders"),
        suppliers
    )
    embeddedServer(CIO, host = host, port = port) {
        hubModule(suppliers, journal)
        monitor.subscribe(ApplicationStopped) {
            journal.close()
            http?.close()
        }
    }.start(wait = true)
}

private fun bitrefillApiKey(): String? {
    val inline = System.getenv("BITREFILL_API_KEY")?.trim()?.takeIf(String::isNotEmpty)
    val filename = System.getenv("BITREFILL_API_KEY_FILE")?.trim()?.takeIf(String::isNotEmpty)
    require(inline == null || filename == null) { "Configure only one Bitrefill API key source" }
    val key = inline ?: filename?.let {
        val path = Path.of(it)
        require(Files.size(path) in 1..8192) { "Bitrefill API key file is empty or too large" }
        Files.readString(path).trim()
    } ?: return null
    require(key.length in 1..4096 && key.none(Char::isWhitespace)) {
        "Invalid Bitrefill API key format"
    }
    return key
}

fun Application.hubModule(
    suppliers: List<ServiceSupplier> = emptyList(),
    orders: ServiceOrderJournal? = null
) {
    val wireJson = Json { encodeDefaults = true }
    install(ContentNegotiation) {
        json(wireJson)
    }
    routing {
        get(HubWidgetProtocol.CATALOG_PATH) {
            if (!call.requireClientMetadata()) return@get
            call.serviceResponse {
                val contracts = call.supportedContracts()
                val widgets = if (HubWidgetProtocol.SERVICE_CONTRACT in contracts) {
                    suppliers.flatMap { it.catalog() }
                        .map {
                            it.widget(call.request.headers[HttpHeaders.AcceptLanguage].orEmpty())
                        }
                        .distinctBy { it.id }
                } else {
                    emptyList()
                }
                call.respond(HubWidgetCatalog(widgets = widgets))
            }
        }
        post("${HubWidgetProtocol.CATALOG_PATH}/{widgetId}/content") {
            if (!call.requireClientMetadata()) return@post
            call.serviceResponse {
                val widgetId = call.parameters["widgetId"]
                val catalog = if (HubWidgetProtocol.SERVICE_CONTRACT in call.supportedContracts()) {
                    suppliers.flatMap { it.catalog() }.firstOrNull { it.widget.id == widgetId }
                } else {
                    null
                }
                if (catalog == null) throw ServiceHttpFailure(404, "widget_not_found")
                val request = wireJson.decodeFromString<HubWidgetContentRequest>(call.boundedBody())
                val variant = catalog.widget.variants.firstOrNull { it.id == request.variantId }
                    ?: throw ServiceHttpFailure(404, "widget_not_found")
                if (request.configuration.size > 16 || request.configuration.any { (key, value) ->
                        key.length > 128 || value.length > 256
                    }
                ) {
                    throw ServiceHttpFailure(400, "invalid_request")
                }
                val kind = if (variant.template == "service-topup") "topup" else "package"
                call.respond(
                    HubWidgetContent(
                        widgetId = catalog.widget.id,
                        variantId = request.variantId,
                        contract = HubWidgetProtocol.SERVICE_CONTRACT,
                        service = catalog.content.copy(
                            offers = catalog.content.offers.filter {
                                it.kind ==
                                    kind
                            }
                        )
                    )
                )
            }
        }
        put("/hub/v1/orders/{orderId}") {
            if (!call.requireClientMetadata()) return@put
            call.serviceResponse {
                if (HubWidgetProtocol.SERVICE_CONTRACT !in call.supportedContracts()) {
                    throw ServiceHttpFailure(400, "unsupported_contract")
                }
                val journal = orders ?: throw ServiceHttpFailure(503, "service_unavailable")
                val request = wireJson.decodeFromString<HubServiceOrderRequest>(call.boundedBody())
                call.respond(
                    journal.put(call.parameters["orderId"].orEmpty(), call.orderToken(), request)
                )
            }
        }
        get("/hub/v1/orders/{orderId}") {
            if (!call.requireClientMetadata()) return@get
            call.serviceResponse {
                val journal = orders ?: throw ServiceHttpFailure(404, "order_not_found")
                call.respond(journal.get(call.parameters["orderId"].orEmpty(), call.orderToken()))
            }
        }
    }
}

private suspend fun ApplicationCall.requireClientMetadata(): Boolean {
    response.header(HttpHeaders.CacheControl, "no-store")
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

private fun ApplicationCall.supportedContracts(): Set<String> =
    request.headers[HubRequestHeaders.CONTRACTS].orEmpty().split(',').map(String::trim).toSet()

private fun ApplicationCall.orderToken(): String {
    val authorization = request.headers[HttpHeaders.Authorization].orEmpty()
    if (!authorization.startsWith("Bearer ")) throw ServiceHttpFailure(401, "order_unauthorized")
    return authorization.removePrefix("Bearer ")
}

private suspend fun ApplicationCall.boundedBody(): String {
    val bytes = ByteArray(8193)
    val channel = receiveChannel()
    var size = 0
    while (size < bytes.size) {
        val count = channel.readAvailable(bytes, size, bytes.size - size)
        if (count < 0) break
        size += count
    }
    if (size >= bytes.size) throw ServiceHttpFailure(413, "request_too_large")
    return bytes.decodeToString(endIndex = size, throwOnInvalidSequence = true)
}

private suspend fun ApplicationCall.serviceResponse(block: suspend () -> Unit) {
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: ServiceHttpFailure) {
        respond(HttpStatusCode.fromValue(failure.status), HubApiError(failure.code))
    } catch (_: SupplierUnavailable) {
        respond(HttpStatusCode.ServiceUnavailable, HubApiError("supplier_unavailable"))
    } catch (_: ServiceRequestRejected) {
        respond(HttpStatusCode.ServiceUnavailable, HubApiError("supplier_unavailable"))
    } catch (_: SerializationException) {
        respond(HttpStatusCode.BadRequest, HubApiError("invalid_request"))
    } catch (_: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, HubApiError("invalid_request"))
    } catch (_: Exception) {
        respond(HttpStatusCode.InternalServerError, HubApiError("backend_unavailable"))
    }
}
