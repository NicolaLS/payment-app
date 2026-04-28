package xyz.lilsus.papp.domain.model

import kotlinx.coroutines.flow.StateFlow

sealed class PayInvoiceRequestState {
    data object Loading : PayInvoiceRequestState()
    data class Success(val invoice: PaidInvoice) : PayInvoiceRequestState()
    data class Failure(val error: AppError) : PayInvoiceRequestState()
}

interface PayInvoiceRequest {
    val state: StateFlow<PayInvoiceRequestState>
    fun cancel()
}
