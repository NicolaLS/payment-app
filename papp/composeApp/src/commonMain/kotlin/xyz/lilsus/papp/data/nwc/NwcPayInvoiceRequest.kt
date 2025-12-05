package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcRequest
import io.github.nostr.nwc.model.NwcRequestState
import io.github.nostr.nwc.model.PayInvoiceResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState

internal class NwcPayInvoiceRequest(
    private val request: NwcRequest<PayInvoiceResult>,
    private val scope: CoroutineScope,
    private val invalidateHandle: suspend () -> Unit
) : PayInvoiceRequest {
    private val _state =
        MutableStateFlow<PayInvoiceRequestState>(PayInvoiceRequestState.Loading)
    override val state: StateFlow<PayInvoiceRequestState> = _state.asStateFlow()

    private val stateJob: Job = scope.launch {
        request.state.collect { state ->
            when (state) {
                NwcRequestState.Loading -> {
                    _state.value = PayInvoiceRequestState.Loading
                }

                is NwcRequestState.Success -> {
                    _state.value = PayInvoiceRequestState.Success(
                        invoice = PaidInvoice(
                            preimage = state.value.preimage,
                            feesPaidMsats = state.value.feesPaid?.msats
                        )
                    )
                }

                is NwcRequestState.Failure -> {
                    val exception = state.failure.toAppErrorException()
                    _state.value = PayInvoiceRequestState.Failure(
                        error = exception.error
                    )
                    scope.launch { invalidateHandle() }
                }
            }
        }
    }

    override fun cancel() {
        request.cancel()
        stateJob.cancel()
    }
}
