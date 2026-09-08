package xyz.lilsus.raylsuite.integration.hub

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata
import xyz.lilsus.raylsuite.core.hubapi.HubMetricContent
import xyz.lilsus.raylsuite.core.hubapi.HubRequestHeaders
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetContent
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetContentRequest
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetDescriptor
import xyz.lilsus.raylsuite.core.hubapi.HubWidgetProtocol

/** Caller owns [client]. An absent endpoint leaves all local Hub features usable. */
class KtorHubWidgetCatalogClient(
    baseUrl: String?,
    private val metadata: HubClientMetadata,
    private val client: HttpClient
) {
    private val endpoint = baseUrl?.trim()?.takeIf(String::isNotEmpty)?.validEndpoint()
    private val json = Json { ignoreUnknownKeys = true }
    private val contracts = metadata.supportedContracts.intersect(
        HubWidgetProtocol.supportedContracts
    )

    suspend fun fetchCatalog(locale: String): HubWidgetCatalogResult {
        val response = request(HubWidgetProtocol.CATALOG_PATH, locale)
        if (response is Response.Failure) return HubWidgetCatalogResult.Unavailable(response.reason)
        return try {
            val document = json.parseToJsonElement((response as Response.Body).text) as? JsonObject
                ?: return HubWidgetCatalogResult.Unavailable(
                    HubWidgetUnavailableReason.InvalidResponse
                )
            if (document.protocolVersion() != HubWidgetProtocol.VERSION) {
                return HubWidgetCatalogResult.Unavailable(
                    HubWidgetUnavailableReason.UnsupportedProtocol
                )
            }
            val items = document["widgets"] as? JsonArray
                ?: return HubWidgetCatalogResult.Unavailable(
                    HubWidgetUnavailableReason.InvalidResponse
                )
            if (items.size > MAX_WIDGETS) {
                return HubWidgetCatalogResult.Unavailable(
                    HubWidgetUnavailableReason.InvalidResponse
                )
            }
            val seen = mutableSetOf<String>()
            val widgets = items.mapNotNull { item ->
                val descriptor = runCatching {
                    json.decodeFromJsonElement<HubWidgetDescriptor>(item)
                }.getOrNull() ?: return@mapNotNull null
                descriptor.takeIf { it.isSupported() && seen.add(it.id) }
            }
            HubWidgetCatalogResult.Available(widgets, skippedCount = items.size - widgets.size)
        } catch (_: SerializationException) {
            HubWidgetCatalogResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse)
        } catch (_: IllegalArgumentException) {
            HubWidgetCatalogResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse)
        }
    }

    suspend fun fetchContent(
        widgetId: String,
        variantId: String,
        configuration: Map<String, String>,
        locale: String
    ): HubWidgetContentResult {
        if (HubWidgetProtocol.METRIC_CONTRACT !in contracts) {
            return HubWidgetContentResult.Unavailable(
                HubWidgetUnavailableReason.UnsupportedProtocol
            )
        }
        if (!widgetId.isIdentifier() || widgetId.startsWith(
                "local."
            ) || !variantId.isIdentifier() ||
            configuration.size > MAX_FIELDS ||
            configuration.any { (key, value) ->
                !key.isIdentifier() ||
                    value.length > MAX_FIELD_LENGTH
            }
        ) {
            return HubWidgetContentResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse)
        }
        val response = request(
            "${HubWidgetProtocol.CATALOG_PATH}/$widgetId/content",
            locale,
            json.encodeToString(HubWidgetContentRequest(variantId, configuration))
        )
        if (response is Response.Failure) return HubWidgetContentResult.Unavailable(response.reason)
        return try {
            val document = json.parseToJsonElement((response as Response.Body).text) as? JsonObject
                ?: return HubWidgetContentResult.Unavailable(
                    HubWidgetUnavailableReason.InvalidResponse
                )
            if (document.protocolVersion() != HubWidgetProtocol.VERSION) {
                return HubWidgetContentResult.Unavailable(
                    HubWidgetUnavailableReason.UnsupportedProtocol
                )
            }
            val content = json.decodeFromJsonElement<HubWidgetContent>(document)
            when {
                content.protocolVersion != HubWidgetProtocol.VERSION ||
                    content.contract !in contracts ->
                    HubWidgetContentResult.Unavailable(
                        HubWidgetUnavailableReason.UnsupportedProtocol
                    )

                content.widgetId != widgetId || content.variantId != variantId ||
                    !content.metric.isValid() ->
                    HubWidgetContentResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse)

                else -> HubWidgetContentResult.Available(content.metric)
            }
        } catch (_: SerializationException) {
            HubWidgetContentResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse)
        } catch (_: IllegalArgumentException) {
            HubWidgetContentResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse)
        }
    }

    private suspend fun request(path: String, locale: String, body: String? = null): Response {
        val base = endpoint ?: return Response.Failure(HubWidgetUnavailableReason.NotConfigured)
        return try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                client.prepareRequest(base + path) {
                    expectSuccess = false
                    method = if (body == null) HttpMethod.Get else HttpMethod.Post
                    metadataHeaders(locale)
                    if (body != null) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                }.execute { response ->
                    when {
                        response.status == HttpStatusCode.NotFound ->
                            Response.Failure(HubWidgetUnavailableReason.NotFound)

                        !response.status.isSuccess() -> Response.Failure(
                            HubWidgetUnavailableReason.HttpError
                        )

                        response.contentType()?.match(ContentType.Application.Json) != true ->
                            Response.Failure(HubWidgetUnavailableReason.InvalidResponse)

                        else -> {
                            val bytes = ByteArray(MAX_RESPONSE_BYTES + 1)
                            val channel = response.bodyAsChannel()
                            var length = 0
                            while (length < bytes.size) {
                                val read = channel.readAvailable(bytes, length, bytes.size - length)
                                if (read < 0) break
                                length += read
                            }
                            if (length > MAX_RESPONSE_BYTES) {
                                Response.Failure(HubWidgetUnavailableReason.InvalidResponse)
                            } else {
                                val text = runCatching {
                                    bytes.decodeToString(
                                        endIndex = length,
                                        throwOnInvalidSequence = true
                                    )
                                }.getOrNull()
                                if (text ==
                                    null
                                ) {
                                    Response.Failure(HubWidgetUnavailableReason.InvalidResponse)
                                } else {
                                    Response.Body(text)
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Response.Failure(HubWidgetUnavailableReason.Offline)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Response.Failure(HubWidgetUnavailableReason.Offline)
        }
    }

    private fun HttpRequestBuilder.metadataHeaders(locale: String) {
        header(HubRequestHeaders.APP, metadata.app)
        header(HubRequestHeaders.VERSION, metadata.version)
        header(HubRequestHeaders.BUILD, metadata.build)
        header(HubRequestHeaders.PLATFORM, metadata.platform)
        header(HubRequestHeaders.CONTRACTS, contracts.sorted().joinToString(","))
        header(HttpHeaders.AcceptLanguage, locale)
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    private fun HubWidgetDescriptor.isSupported(): Boolean =
        contract == HubWidgetProtocol.METRIC_CONTRACT && contract in contracts &&
            id.isIdentifier() && !id.startsWith("local.") && revision.isNotBlank() &&
            revision.length <= 128 &&
            title.isNotBlank() && title.length <= 120 && (description?.length ?: 0) <= 500 &&
            variants.isNotEmpty() && variants.size <= 8 &&
            variants.map { it.id }.distinct().size == variants.size &&
            variants.all {
                it.id.isIdentifier() && it.title.isNotBlank() && it.title.length <= 120 &&
                    it.template == "metric" && it.size in setOf("small", "wide", "large")
            } && fields.size <= MAX_FIELDS && fields.map { it.id }.distinct().size == fields.size &&
            fields.all { field ->
                field.id.isIdentifier() && field.label.isNotBlank() && field.label.length <= 120 &&
                    field.type in setOf("text", "phone", "choice") &&
                    (field.maxLength == null || field.maxLength in 1..MAX_FIELD_LENGTH) &&
                    field.options.size <= 64 &&
                    field.options.map { it.id }.distinct().size == field.options.size &&
                    (field.type != "choice" || field.options.isNotEmpty()) &&
                    field.options.all {
                        it.id.isIdentifier() && it.label.isNotBlank() &&
                            it.label.length <= 120 &&
                            (
                                field.type != "choice" ||
                                    it.id.length <= (field.maxLength ?: MAX_FIELD_LENGTH)
                                )
                    }
            } && actions.size <= 4 && actions.map { it.id }.distinct().size == actions.size &&
            actions.all {
                it.id.isIdentifier() && it.title.isNotBlank() && it.title.length <= 120 &&
                    it.kind == "refresh"
            }

    private sealed interface Response {
        data class Body(val text: String) : Response

        data class Failure(val reason: HubWidgetUnavailableReason) : Response
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val MAX_RESPONSE_BYTES = 262_144
        const val MAX_WIDGETS = 128
        const val MAX_FIELDS = 16
        const val MAX_FIELD_LENGTH = 256
    }
}

private fun JsonObject.protocolVersion(): Int? =
    (get("protocolVersion") as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

private fun String.isIdentifier(): Boolean = matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))

private fun String.validEndpoint(): String? = runCatching {
    val parsed = Url(this)
    val localHttp =
        parsed.protocol == URLProtocol.HTTP &&
            parsed.host in setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")
    takeIf {
        (parsed.protocol == URLProtocol.HTTPS || localHttp) && parsed.user.isNullOrEmpty() &&
            parsed.password.isNullOrEmpty() && parsed.parameters.isEmpty() &&
            parsed.fragment.isEmpty()
    }?.trimEnd('/')
}.getOrNull()

private fun HubMetricContent.isValid(): Boolean =
    value.length <= 128 && value.matches(Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?")) &&
        unit.isNotBlank() && unit.length <= 24 && label.isNotBlank() && label.length <= 120 &&
        runCatching { Instant.parse(asOf) }.isSuccess &&
        (refreshAfterSeconds == null || refreshAfterSeconds in 30..86_400)
