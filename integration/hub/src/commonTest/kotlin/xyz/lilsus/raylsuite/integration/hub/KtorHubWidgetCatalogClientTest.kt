package xyz.lilsus.raylsuite.integration.hub

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata
import xyz.lilsus.raylsuite.core.hubapi.HubRequestHeaders

class KtorHubWidgetCatalogClientTest {
    private val metadata = HubClientMetadata("rayl", "1.0.0", "42", "ios")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun metadataIsSentAndUnsupportedOrMalformedItemsDoNotRemoveSupportedWidgets() = runTest {
        val engine = mockEngine { request ->
            assertEquals("/hub/v1/widgets", request.url.encodedPath)
            assertEquals("rayl", request.headers[HubRequestHeaders.APP])
            assertEquals("1.0.0", request.headers[HubRequestHeaders.VERSION])
            assertEquals("42", request.headers[HubRequestHeaders.BUILD])
            assertEquals("ios", request.headers[HubRequestHeaders.PLATFORM])
            assertEquals("metric/v1,service/v1", request.headers[HubRequestHeaders.CONTRACTS])
            assertEquals("es", request.headers[HttpHeaders.AcceptLanguage])
            respond(
                """
                {
                  "protocolVersion": 1,
                  "widgets": [
                    {
                      "id": "btc-price", "revision": "1", "contract": "metric/v1",
                      "title": "Precio de Bitcoin",
                      "variants": [{"id": "usd", "title": "USD", "template": "metric"}]
                    },
                    {
                      "id": "future", "revision": "1", "contract": "future/v1", "title": "Future",
                      "variants": [{"id": "compact", "title": "Compact", "template": "metric"}]
                    },
                    {
                      "id": "invalid-choice", "revision": "1", "contract": "metric/v1", "title": "Invalid",
                      "variants": [{"id": "compact", "title": "Compact", "template": "metric"}],
                      "fields": [{"id":"currency","label":"Currency","type":"choice","maxLength":2,
                        "options":[{"id":"USD","label":"US dollar"}]}]
                    },
                    17
                  ]
                }
                """.trimIndent(),
                headers = jsonHeaders
            )
        }
        val http = HttpClient(engine)
        try {
            val client = KtorHubWidgetCatalogClient("https://hub.example.test", metadata, http)
            val result = assertIs<HubWidgetCatalogResult.Available>(client.fetchCatalog("es"))
            assertEquals(listOf("btc-price"), result.widgets.map { it.id })
            assertEquals(3, result.skippedCount)
        } finally {
            http.close()
        }
    }

    @Test
    fun missingConfigurationEmptyCatalogueMalformedBodyAndOfflineHaveDistinctResults() = runTest {
        var requests = 0
        val http = HttpClient(
            mockEngine {
                requests++
                when (requests) {
                    1 -> respond("""{"protocolVersion":1,"widgets":[]}""", headers = jsonHeaders)
                    2 -> respond("{broken", headers = jsonHeaders)
                    else -> error("Unavailable transport")
                }
            }
        )
        try {
            val absent = KtorHubWidgetCatalogClient(null, metadata, http)
            assertEquals(
                HubWidgetCatalogResult.Unavailable(HubWidgetUnavailableReason.NotConfigured),
                absent.fetchCatalog("en")
            )
            assertEquals(0, requests)
            val configured = KtorHubWidgetCatalogClient("http://127.0.0.1:8080", metadata, http)
            assertEquals(HubWidgetCatalogResult.Available(emptyList()), configured.fetchCatalog("en"))
            assertEquals(
                HubWidgetCatalogResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse),
                configured.fetchCatalog("en")
            )
            assertEquals(
                HubWidgetCatalogResult.Unavailable(HubWidgetUnavailableReason.Offline),
                configured.fetchCatalog("en")
            )
        } finally {
            http.close()
        }
    }

    @Test
    fun contentPreservesExactValueAndRejectsMismatchedIdentity() = runTest {
        var requests = 0
        val http = HttpClient(
            mockEngine { request ->
                requests++
                assertEquals("/hub/v1/widgets/btc-price/content", request.url.encodedPath)
                assertEquals("42", request.headers[HubRequestHeaders.BUILD])
                assertEquals("en", request.headers[HttpHeaders.AcceptLanguage])
                if (requests == 3) {
                    respond("""{"code":"widget_not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                } else {
                    respond(
                        """
                    {
                      "protocolVersion": 1, "contract": "metric/v1",
                      "widgetId": "${if (requests == 1) "btc-price" else "wrong-widget"}",
                      "variantId": "usd",
                      "metric": {"value":"123456.789123456789","unit":"USD","label":"Bitcoin",
                        "asOf":"2026-09-07T12:00:00Z","refreshAfterSeconds":60}
                    }
                        """.trimIndent(),
                        headers = jsonHeaders
                    )
                }
            }
        ) { expectSuccess = true }
        try {
            val client = KtorHubWidgetCatalogClient("https://hub.example.test", metadata, http)
            val result = assertIs<HubWidgetContentResult.Available>(client.fetchContent("btc-price", "usd", emptyMap(), "en"))
            assertEquals("123456.789123456789", result.content.metric?.value)
            assertEquals(
                HubWidgetContentResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse),
                client.fetchContent("btc-price", "usd", emptyMap(), "en")
            )
            assertEquals(
                HubWidgetContentResult.Unavailable(HubWidgetUnavailableReason.InvalidResponse),
                client.fetchContent("local.contacts", "single", emptyMap(), "en")
            )
            val unsupported = KtorHubWidgetCatalogClient(
                "https://hub.example.test",
                metadata.copy(supportedContracts = emptySet()),
                http
            )
            assertEquals(
                HubWidgetContentResult.Unavailable(HubWidgetUnavailableReason.UnsupportedProtocol),
                unsupported.fetchContent("btc-price", "usd", emptyMap(), "en")
            )
            assertEquals(2, requests)
            assertEquals(
                HubWidgetContentResult.Unavailable(HubWidgetUnavailableReason.NotFound),
                client.fetchContent("btc-price", "usd", emptyMap(), "en")
            )
        } finally {
            http.close()
        }
    }
}

private fun TestScope.mockEngine(handler: MockRequestHandler): MockEngine = MockEngine(
    MockEngineConfig().apply {
        dispatcher = StandardTestDispatcher(testScheduler)
        addHandler(handler)
    }
)
