package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.repository.NwcWalletRepository

/**
 * Use case responsible for paying a Lightning invoice via the connected NWC wallet.
 */
class PayInvoiceUseCase(
    private val repository: NwcWalletRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Starts a pay request for the provided [invoice] and returns a handle that can be observed
     * for completion. Callers should cancel the request when they no longer need updates.
     */
    suspend operator fun invoke(invoice: String, amountMsats: Long? = null): PayInvoiceRequest =
        withContext(dispatcher) {
            repository.startPayInvoiceRequest(
                invoice = invoice,
                amountMsats = amountMsats
            )
        }
}
