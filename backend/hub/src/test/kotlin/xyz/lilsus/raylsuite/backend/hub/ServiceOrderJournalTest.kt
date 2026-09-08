package xyz.lilsus.raylsuite.backend.hub

import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import xyz.lilsus.raylsuite.core.hubapi.HubServiceContent
import xyz.lilsus.raylsuite.core.hubapi.HubServiceLightningPayment
import xyz.lilsus.raylsuite.core.hubapi.HubServiceMoney
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOffer
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrderRequest

class ServiceOrderJournalTest {
    private val orderId = "77777777-7777-4777-8777-777777777777"
    private val token = "a".repeat(64)
    private val request =
        HubServiceOrderRequest("service.test", "topup", "r1", "offer1", "+50370000000", "500")

    @Test
    fun duplicatePreparationSurvivesRestartAndTokenOrBodyCannotChange() = runTest {
        val directory = Files.createTempDirectory("hub-order-test")
        val supplier = FakeSupplier()
        val clock = TestClock()
        try {
            ServiceOrderJournal(directory, listOf(supplier), clock).use { journal ->
                val prepared = journal.put(orderId, token, request)
                assertEquals("awaiting_payment", prepared.state)
                assertEquals(prepared, journal.put(orderId, token, request))
                assertEquals(1, supplier.submissions)
                assertEquals(
                    401,
                    assertFailsWith<ServiceHttpFailure> {
                        journal.get(orderId, "b".repeat(64))
                    }.status
                )
                assertEquals(
                    "order_conflict",
                    assertFailsWith<ServiceHttpFailure> {
                        journal.put(orderId, token, request.copy(amountMinor = "600"))
                    }.code
                )
                assertFalse(Files.readString(directory.resolve("$orderId.json")).contains(token))
            }
            ServiceOrderJournal(directory, listOf(supplier), clock).use { journal ->
                assertEquals("awaiting_payment", journal.put(orderId, token, request).state)
                assertEquals(1, supplier.submissions)
                clock.now = clock.now.plusSeconds(11)
                supplier.status = SupplierOrderStatus("expired", "unpaid", "pending")
                assertEquals("expired", journal.get(orderId, token).state)
                clock.now = clock.now.plusSeconds(11)
                supplier.status = SupplierOrderStatus("failed", "paid", "failed")
                val failedDelivery = journal.get(orderId, token)
                assertEquals("paid", failedDelivery.paymentStatus)
                assertEquals("failed", failedDelivery.fulfillmentStatus)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun uncertainSubmitAndCorruptJournalNeverCreateAnotherInvoice() = runTest {
        val directory = Files.createTempDirectory("hub-order-test")
        val supplier = FakeSupplier().apply { failAfterSubmission = true }
        try {
            ServiceOrderJournal(directory, listOf(supplier), TestClock()).use { journal ->
                assertEquals("unknown", journal.put(orderId, token, request).state)
            }
            ServiceOrderJournal(directory, listOf(supplier), TestClock()).use { journal ->
                assertEquals("unknown", journal.put(orderId, token, request).state)
                assertEquals("unknown", journal.get(orderId, token).state)
                assertEquals(1, supplier.submissions)
                Files.writeString(directory.resolve("$orderId.json"), "{broken")
                assertEquals(
                    "order_storage_unavailable",
                    assertFailsWith<ServiceHttpFailure> {
                        journal.put(orderId, token, request)
                    }.code
                )
                assertEquals(1, supplier.submissions)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun definitiveValidationBeforeSubmitLeavesNoOrderToRecover() = runTest {
        val directory = Files.createTempDirectory("hub-order-test")
        val supplier = FakeSupplier().apply { rejectBeforeSubmission = true }
        try {
            ServiceOrderJournal(directory, listOf(supplier), TestClock()).use { journal ->
                assertEquals(
                    "invalid_amount",
                    assertFailsWith<ServiceHttpFailure> {
                        journal.put(orderId, token, request)
                    }.code
                )
                assertEquals(0, supplier.submissions)
                assertEquals(
                    "order_not_found",
                    assertFailsWith<ServiceHttpFailure> {
                        journal.get(orderId, token)
                    }.code
                )
                supplier.rejectBeforeSubmission = false
                assertEquals("awaiting_payment", journal.put(orderId, token, request).state)
                assertEquals(1, supplier.submissions)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}

private class FakeSupplier : ServiceSupplier {
    override val id = "test"
    var submissions = 0
    var rejectBeforeSubmission = false
    var failAfterSubmission = false
    var status = SupplierOrderStatus(
        "awaiting_payment",
        "unpaid",
        "pending",
        HubServiceLightningPayment("UNIT_TEST_ONLY", "100000", "2026-09-08T13:00:00Z")
    )
    private val amount = HubServiceMoney("500", "USD")

    override suspend fun catalog() = listOf(
        SupplierCatalog(
            "service.test",
            HubServiceContent(
                "Test",
                "SV",
                "+503",
                listOf(HubServiceOffer("offer1", "Five dollars", kind = "topup", amount = amount)),
                "r1"
            )
        )
    )

    override suspend fun prepare(
        request: HubServiceOrderRequest,
        beforeSubmit: suspend () -> Unit
    ): SupplierPreparedOrder {
        if (rejectBeforeSubmission) throw ServiceRequestRejected("invalid_amount")
        beforeSubmit()
        submissions++
        if (failAfterSubmission) throw SupplierUnavailable()
        return SupplierPreparedOrder("supplier-invoice", "Test", "Five dollars", amount, status)
    }

    override suspend fun read(reference: String) = status
}

private class TestClock : Clock() {
    var now = Instant.parse("2026-09-08T12:00:00Z")
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = now
}
