package xyz.lilsus.papp.domain.usecases

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.Result
import xyz.lilsus.papp.domain.repository.NwcWalletRepository

/**
 * Use case responsible for paying a Lightning invoice via the connected NWC wallet.
 */
class PayInvoiceUseCase(
    private val repository: NwcWalletRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Attempts to pay the provided [invoice] and exposes the operation state as a [Flow].
     *
     * @param amountMsats Optional override for invoices that omit an amount.
     */
    operator fun invoke(invoice: String, amountMsats: Long? = null): Flow<Result<PaidInvoice>> =
        flow {
            emit(Result.Loading)
            val payment = withContext(dispatcher) {
                repository.payInvoice(
                    invoice = invoice,
                    amountMsats = amountMsats
                )
            }
            emit(Result.success(payment))
        }.catch { throwable ->
            if (throwable is CancellationException) throw throwable
            val (error, cause) = when (throwable) {
                is AppErrorException -> throwable.error to throwable.cause
                else -> AppError.Unexpected(throwable.message) to throwable
            }
            emit(Result.error(error = error, cause = cause))
        }
}
