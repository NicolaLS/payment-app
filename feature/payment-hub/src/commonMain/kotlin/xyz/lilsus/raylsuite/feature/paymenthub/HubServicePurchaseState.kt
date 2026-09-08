package xyz.lilsus.raylsuite.feature.paymenthub

import xyz.lilsus.raylsuite.core.hubapi.HubServiceOffer
import xyz.lilsus.raylsuite.core.hubapi.HubServiceOrder

enum class HubServiceError {
    InvalidPhone,
    InvalidAmount,
    SelectOffer,
    Unavailable,
    Changed,
    SaveFailed,
    InvalidInvoice
}

/** Values only: native platforms own purchase sheets and payment-sheet handoff. */
data class HubServicePurchaseState(
    val widgetId: String,
    val title: String,
    val phone: String,
    val offers: List<HubServiceOffer> = emptyList(),
    val selectedOfferId: String? = null,
    val amountInput: String = "",
    val busy: Boolean = false,
    val error: HubServiceError? = null,
    val order: HubServiceOrder? = null,
    val canPay: Boolean = false
) {
    val selectedOffer: HubServiceOffer? get() = offers.firstOrNull { it.id == selectedOfferId }
}
