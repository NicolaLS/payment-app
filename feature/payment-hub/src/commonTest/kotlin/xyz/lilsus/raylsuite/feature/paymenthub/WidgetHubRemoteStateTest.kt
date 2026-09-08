package xyz.lilsus.raylsuite.feature.paymenthub

import com.russhwolf.settings.MapSettings
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.integration.hub.KtorHubWidgetCatalogClient

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetHubRemoteStateTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private val draft = HubWidgetDraft(
        definitionId = "example-balance",
        kind = HubWidgetKind.Metric,
        variant = LocalHubWidgets.Single.copy(id = "compact"),
        configuration = mapOf("account" to "A")
    )

    @Test
    fun changedConfigurationDoesNotDisplayPreviousValueWhenRefreshFails() = runTest {
        val repo = DefaultPaymentHubRepository(MapSettings())
        val widget = assertNotNull(repo.saveWidget(draft))
        var contentRequests = 0
        val http = mockHttp { request ->
            if (request.url.encodedPath == "/hub/v1/widgets") {
                respond(catalog("compact"), headers = jsonHeaders)
            } else {
                contentRequests++
                if (contentRequests == 1) {
                    respond(content(), headers = jsonHeaders)
                } else {
                    respond("{}", HttpStatusCode.ServiceUnavailable, jsonHeaders)
                }
            }
        }
        val vm = model(repo, http)
        try {
            runCurrent()
            assertEquals("42.50", vm.state.value.widgets.single().metric?.value)
            assertFalse(vm.state.value.widgets.single().unavailable)

            assertNotNull(repo.saveWidget(draft.copy(configuration = mapOf("account" to "B")), widget.id))
            runCurrent()

            assertEquals(2, contentRequests)
            assertEquals(mapOf("account" to "B"), repo.hub.value.widgets.single().configuration)
            assertNull(vm.state.value.widgets.single().metric)
            assertTrue(vm.state.value.widgets.single().unavailable)
        } finally {
            vm.clear()
            http.close()
        }
    }

    @Test
    fun removedVariantKeepsSavedWidgetAndMarksItUnavailable() = runTest {
        val repo = DefaultPaymentHubRepository(MapSettings())
        val widget = assertNotNull(repo.saveWidget(draft))
        var availableVariant = "compact"
        var contentRequests = 0
        val http = mockHttp { request ->
            if (request.url.encodedPath == "/hub/v1/widgets") {
                respond(catalog(availableVariant), headers = jsonHeaders)
            } else {
                contentRequests++
                respond(content(), headers = jsonHeaders)
            }
        }
        val vm = model(repo, http)
        try {
            runCurrent()
            assertEquals("42.50", vm.state.value.widgets.single().metric?.value)
            assertFalse(vm.state.value.widgets.single().unavailable)

            availableVariant = "replacement"
            vm.refreshCatalog()
            runCurrent()

            assertEquals(widget, repo.hub.value.widgets.single())
            assertEquals(1, contentRequests)
            assertTrue(vm.state.value.widgets.single().unavailable)

            vm.editWidget(widget.id)
            assertEquals("replacement", vm.state.value.selectedVariant?.id)
        } finally {
            vm.clear()
            http.close()
        }
    }

    private fun TestScope.model(repository: PaymentHubRepository, http: HttpClient) = WidgetHubViewModel(
        repository = repository,
        host = PaymentHubController(repository, this),
        defaultCurrencyCode = { "USD" },
        catalog = KtorHubWidgetCatalogClient(
            "https://hub.example.test",
            HubClientMetadata("com.nicolasusca.rayl", "1.0.0", "42", "ios"),
            http
        ),
        dispatcher = StandardTestDispatcher(testScheduler)
    )

    private fun TestScope.mockHttp(handler: MockRequestHandler) = HttpClient(
        MockEngine(
            MockEngineConfig().apply {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler(handler)
            }
        )
    )

    private fun catalog(variant: String) = """
        {"protocolVersion":1,"widgets":[{
          "id":"example-balance","revision":"1","contract":"metric/v1","title":"Balance",
          "variants":[{"id":"$variant","title":"Compact","template":"metric","size":"small"}],
          "fields":[{"id":"account","label":"Account","type":"text","required":true}]
        }]}
    """.trimIndent()

    private fun content() = """
        {"protocolVersion":1,"widgetId":"example-balance","variantId":"compact","contract":"metric/v1",
          "metric":{"value":"42.50","unit":"USD","label":"Balance","asOf":"2026-09-07T12:00:00Z"}}
    """.trimIndent()
}
