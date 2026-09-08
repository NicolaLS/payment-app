package xyz.lilsus.raylsuite.feature.paymenthub

import com.russhwolf.settings.MapSettings
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata
import xyz.lilsus.raylsuite.core.hubapi.HubServiceContent
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOffer
import xyz.lilsus.raylsuite.core.settings.SecureStringStore
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.integration.hub.KtorHubWidgetCatalogClient

@OptIn(ExperimentalCoroutinesApi::class)
class HubServicePurchaseControllerTest {
    @Test
    fun lostRequestSurvivesRecreationAndResumesWithSameIdentityAndCredential() = runTest {
        val values = mutableMapOf<String, String>()
        val secure = object : SecureStringStore {
            override fun getStringOrNull(key: String) = values[key]
            override fun putString(key: String, value: String) {
                values[key] = value
            }
            override fun remove(key: String) {
                values.remove(key)
            }
            override fun clear() = values.clear()
        }
        val store = HubServiceOrderStore(secure, "https://hub.example.test")
        var puts = 0
        var gets = 0
        val ids = mutableSetOf<String>()
        val credentials = mutableSetOf<String?>()
        val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val http = HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        val persisted = assertNotNull(store.load())
                        assertTrue(request.url.encodedPath.endsWith(persisted.id))
                        ids += persisted.id
                        credentials += request.headers[HttpHeaders.Authorization]
                        if (request.method == HttpMethod.Get) {
                            gets++
                            if (puts == 1) {
                                respond("""{"code":"order_not_found"}""", HttpStatusCode.NotFound, jsonHeaders)
                            } else {
                                respond(orderJson(persisted.id), headers = jsonHeaders)
                            }
                        } else {
                            puts++
                            if (puts == 1) error("Simulated lost request")
                            respond(orderJson(persisted.id), headers = jsonHeaders)
                        }
                    }
                }
            )
        )
        val client = KtorHubWidgetCatalogClient(
            "https://hub.example.test",
            HubClientMetadata("rayl", "1", "1", "android"),
            http
        )
        val firstScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val secondScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val host = PaymentHubController(DefaultPaymentHubRepository(MapSettings()), backgroundScope)
        val widget = HubWidget(
            "widget",
            "service.claro-sv",
            HubWidgetKind.Service,
            LocalHubWidgets.Row.copy(id = "packages-row", template = "service-packages"),
            configuration = mapOf("phone" to "70000000")
        )
        val content = HubServiceContent(
            "Claro",
            "SV",
            "+503",
            listOf(HubServiceOffer("bundle", "Example bundle", kind = "package")),
            "1"
        )
        try {
            val first = HubServicePurchaseController(client, store, host, { "en" }, firstScope)
            first.open(widget, content, null)
            first.prepare()
            runCurrent()
            assertEquals(1, puts)
            assertTrue(first.state.value.hasOrder)
            assertTrue(first.state.value.purchase?.offers?.isEmpty() == true)
            firstScope.cancel()

            val second = HubServicePurchaseController(client, store, host, { "en" }, secondScope)
            second.openSaved()
            runCurrent()
            assertEquals(1, gets)
            assertEquals(2, puts)
            assertEquals("unknown", second.state.value.purchase?.order?.state)
            second.open(widget, content, null)
            runCurrent()
            assertEquals(2, puts)
            assertEquals(1, ids.size)
            assertEquals(1, credentials.size)
        } finally {
            firstScope.cancel()
            secondScope.cancel()
            http.close()
        }
    }

    private fun orderJson(id: String) = """
        {"protocolVersion":1,"orderId":"$id","serviceTitle":"Claro","itemTitle":"Example bundle",
         "phone":"+50370000000","state":"unknown","paymentStatus":"unknown","fulfillmentStatus":"unknown",
         "createdAt":"2026-09-07T12:00:00Z","updatedAt":"2026-09-07T12:00:00Z"}
    """.trimIndent()
}
