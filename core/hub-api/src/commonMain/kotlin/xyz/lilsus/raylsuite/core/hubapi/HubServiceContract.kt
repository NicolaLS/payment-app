package xyz.lilsus.raylsuite.core.hubapi

import kotlinx.serialization.Serializable

/** Exact service denomination. This is not the Lightning invoice price. */
@Serializable
data class HubServiceMoney(val minor: String, val currency: String, val fractionDigits: Int = 2)

@Serializable
data class HubServiceAmountRange(
    val minMinor: String,
    val maxMinor: String,
    val stepMinor: String,
    val currency: String,
    val fractionDigits: Int = 2
)

/** Supplier-neutral, short-lived catalog selection; supplier identifiers remain server-owned. */
@Serializable
data class HubServiceOffer(
    val id: String,
    val title: String,
    val description: String? = null,
    val kind: String,
    val amount: HubServiceMoney? = null,
    val range: HubServiceAmountRange? = null
)

@Serializable
data class HubServiceContent(
    val title: String,
    val country: String,
    val callingCode: String,
    val offers: List<HubServiceOffer>,
    val revision: String
)

/** PUT /hub/v1/orders/{client UUID}; Bearer recovery credential is a separate header. */
@Serializable
data class HubServiceOrderRequest(
    val widgetId: String,
    val variantId: String,
    val revision: String,
    val offerId: String,
    val phone: String,
    val amountMinor: String? = null
)

@Serializable
data class HubServiceLightningPayment(
    val invoice: String,
    val amountMsat: String,
    val expiresAt: String
)

/** Payment and supplier delivery are independent facts. Unknown outcomes never mean failure. */
@Serializable
data class HubServiceOrder(
    val protocolVersion: Int = HubWidgetProtocol.VERSION,
    val orderId: String,
    val serviceTitle: String,
    val itemTitle: String,
    val phone: String,
    val requestedAmount: HubServiceMoney? = null,
    val state: String,
    val paymentStatus: String,
    val fulfillmentStatus: String,
    val payment: HubServiceLightningPayment? = null,
    val createdAt: String,
    val updatedAt: String
)
