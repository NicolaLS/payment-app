package xyz.lilsus.raylsuite.backend.hub

import fr.acinq.bitcoin.Chain
import fr.acinq.bitcoin.Crypto
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.utils.Either
import fr.acinq.lightning.Feature
import fr.acinq.lightning.FeatureSupport
import fr.acinq.lightning.Features
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.utils.toByteVector32
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrderRequest

class BitrefillSupplierTest {
    @Test
    fun exactMoneyDoesNotRoundOrConfuseAccountPriceWithServiceValue() {
        assertEquals("701", JsonPrimitive("7.01").exactMinor(2)?.toString())
        assertNull(JsonPrimitive("7.001").exactMinor(2))
        assertNull(JsonPrimitive("1e99999999").exactMinor(2))
        assertNull(JsonPrimitive("2 GB for seven days").exactMinor(2))
    }

    @Test
    fun authenticatedCatalogAndLightningPreparationUseActualPackageIdAndSeparateDelivery() =
        runTest {
            val now = Instant.parse("2026-09-08T12:00:00Z")
            val invoice = invoice(now.epochSecond)
            var markedBeforeSubmit = false
            var posts = 0
            var orderStatus = "created"
            val headers =
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            val http = HttpClient(
                MockEngine(
                    MockEngineConfig().apply {
                        dispatcher = StandardTestDispatcher(testScheduler)
                        addHandler { request ->
                            assertEquals(
                                "Bearer unit-test-key",
                                request.headers[HttpHeaders.Authorization]
                            )
                            val response = when (request.url.encodedPath) {
                                "/v2/products/topup" -> """
                        {"data":{"id":"topup","name":"Claro","country_code":"SV","currency":"USD",
                          "in_stock":true,"range":{"min":7,"max":100,"step":0.01},"packages":[]}}
                                """.trimIndent()

                                "/v2/products/bundles" -> """
                        {"data":{"id":"bundles","name":"Claro packages","country_code":"SV","currency":"USD",
                          "in_stock":true,"packages":[{"id":"bundles<&>Pack A","value":"Pack A","amount":7,"price":5.50}]}}
                                """.trimIndent()

                                else -> {
                                    if (request.method == HttpMethod.Post) {
                                        assertEquals("/v2/invoices", request.url.encodedPath)
                                        assertTrue(markedBeforeSubmit)
                                        posts++
                                        val body = Json.parseToJsonElement(
                                            (request.body as TextContent).text
                                        ) as JsonObject
                                        assertEquals(
                                            JsonPrimitive("lightning"),
                                            body["payment_method"]
                                        )
                                        assertFalse(
                                            (body["auto_pay"] as JsonPrimitive).booleanOrNull!!
                                        )
                                        assertFalse(
                                            (body["send_email"] as JsonPrimitive).booleanOrNull!!
                                        )
                                        val line =
                                            (body["products"] as JsonArray).single() as JsonObject
                                        assertEquals(
                                            JsonPrimitive("bundles<&>Pack A"),
                                            line["package_id"]
                                        )
                                        assertEquals(
                                            JsonPrimitive("+50370000000"),
                                            line["phone_number"]
                                        )
                                        assertNull(line["value"])
                                    }
                                    """
                            {"data":{"id":"11111111-1111-4111-8111-111111111111","status":"unpaid",
                              "payment":{"method":"lightning","status":"unpaid","address":"$invoice"},
                              "orders":[{"id":"order1","status":"$orderStatus"}]}}
                                    """.trimIndent()
                                }
                            }
                            respond(response, headers = headers)
                        }
                    }
                )
            )
            try {
                val supplier = BitrefillSupplier(
                    "unit-test-key",
                    http,
                    "topup",
                    "bundles",
                    clock = Clock.fixed(now, ZoneOffset.UTC)
                )
                val catalog = supplier.catalog().single()
                assertEquals(
                    "700",
                    catalog.content.offers.single {
                        it.kind == "topup"
                    }.range?.minMinor
                )
                val packageOffer = catalog.content.offers.single { it.kind == "package" }
                assertEquals("Pack A", packageOffer.title)
                assertEquals("700", packageOffer.amount?.minor)
                val prepared = supplier.prepare(
                    HubServiceOrderRequest(
                        catalog.widget.id,
                        "packages-row",
                        catalog.content.revision,
                        packageOffer.id,
                        "+50370000000"
                    )
                ) { markedBeforeSubmit = true }
                assertEquals(1, posts)
                assertEquals("awaiting_payment", prepared.status.state)
                val payment = assertNotNull(prepared.status.payment)
                assertEquals("100001", payment.amountMsat)
                assertTrue(Instant.parse(payment.expiresAt).isAfter(now))
                orderStatus = "refunded"
                val result = supplier.read(prepared.reference)
                assertEquals("failed", result.state)
                assertEquals("paid", result.paymentStatus)
                assertEquals("failed", result.fulfillmentStatus)
            } finally {
                http.close()
            }
        }

    private fun invoice(timestampSeconds: Long): String = Bolt11Invoice.create(
        chain = Chain.Mainnet,
        amount = 100_001L.msat,
        paymentHash = Crypto.sha256("unit-test-payment".encodeToByteArray()).toByteVector32(),
        privateKey = PrivateKey(ByteArray(32) { 1 }),
        description = Either.Left("Unit test only"),
        minFinalCltvExpiryDelta = Bolt11Invoice.DEFAULT_MIN_FINAL_EXPIRY_DELTA,
        features = Features(
            Feature.VariableLengthOnion to FeatureSupport.Optional,
            Feature.PaymentSecret to FeatureSupport.Optional
        ),
        timestampSeconds = timestampSeconds
    ).write()
}
