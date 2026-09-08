package xyz.lilsus.raylsuite.backend.hub

import xyz.lilsus.raylsuite.core.hubapi.HubServiceContent
import xyz.lilsus.raylsuite.core.hubapi.HubServiceLightningPayment
import xyz.lilsus.raylsuite.core.hubapi.HubServiceMoney
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrderRequest

/** Supplier behavior is backend-owned. A stored order pins this supplier and its opaque reference. */
interface ServiceSupplier {
    val id: String
    suspend fun catalog(): List<SupplierCatalog>

    /** Validate first, then invoke [beforeSubmit] exactly once immediately before creating an invoice. */
    suspend fun prepare(
        request: HubServiceOrderRequest,
        beforeSubmit: suspend () -> Unit
    ): SupplierPreparedOrder
    suspend fun read(reference: String): SupplierOrderStatus
}

data class SupplierCatalog(val serviceId: String, val content: HubServiceContent)

data class SupplierPreparedOrder(
    val reference: String,
    val serviceTitle: String,
    val itemTitle: String,
    val requestedAmount: HubServiceMoney?,
    val status: SupplierOrderStatus
)

data class SupplierOrderStatus(
    val state: String,
    val paymentStatus: String,
    val fulfillmentStatus: String,
    val payment: HubServiceLightningPayment? = null
)

/** The code is stable and safe to return; never expose supplier responses or credentials. */
class ServiceRequestRejected(val code: String) : Exception(code)

/** An unavailable prepare result may already have created a supplier invoice. Never retry it. */
class SupplierUnavailable : Exception("supplier_unavailable")
