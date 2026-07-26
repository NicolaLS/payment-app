package xyz.lilsus.blip.data.nwc

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice
import fr.acinq.lightning.utils.msat
import io.github.nicolals.nwc.Amount
import io.github.nicolals.nwc.LookupInvoiceParams
import io.github.nicolals.nwc.NwcClient
import io.github.nicolals.nwc.NwcError
import io.github.nicolals.nwc.NwcResult
import io.github.nicolals.nwc.TransactionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.blip.domain.model.AppError
import xyz.lilsus.blip.domain.model.AppErrorException
import xyz.lilsus.blip.domain.model.PaidInvoice
import xyz.lilsus.blip.domain.model.PayInvoiceRequest
import xyz.lilsus.blip.domain.model.PayInvoiceRequestState
import xyz.lilsus.blip.domain.model.PaymentLookupResult
import xyz.lilsus.blip.domain.repository.NwcWalletRepository
import xyz.lilsus.blip.platform.NetworkConnectivity

/**
 * [NwcWalletRepository] implementation backed by [NwcClient].
 *
 * Uses [NwcConnectionManager] to obtain persistent clients, preventing
 * connection thrashing and ensuring reliable background cleanup.
 */
class NwcWalletRepositoryImpl(
    private val connectionManager: NwcConnectionManager,
    private val scope: CoroutineScope,
    private val networkConnectivity: NetworkConnectivity,
    private val payTimeoutMillis: Long = DEFAULT_NWC_PAY_TIMEOUT_MILLIS
) : NwcWalletRepository {

    override suspend fun payInvoice(invoice: Bolt11Invoice, amount: MilliSatoshi?): PaidInvoice {
        if (amount != null) {
            require(amount.msat > 0) { "Amount must be greater than zero." }
        }

        if (!networkConnectivity.isNetworkAvailable()) {
            throw AppErrorException(AppError.NetworkUnavailable)
        }

        val client = connectionManager.getClient()
        val result = client.payInvoice(
            invoice = invoice.write(),
            amount = amount?.let { Amount.fromMsats(it.msat) },
            timeoutMs = payTimeoutMillis,
            verifyOnTimeout = true
        )

        return when (result) {
            is NwcResult.Success -> PaidInvoice(
                preimage = result.value.preimage.toByteVector32OrNull(),
                feesPaid = result.value.feesPaid?.msats?.msat
            )

            is NwcResult.Failure -> throw result.error.toAppErrorException()
        }
    }

    override fun startPayInvoiceRequest(
        invoice: Bolt11Invoice,
        amount: MilliSatoshi?
    ): PayInvoiceRequest {
        if (amount != null) {
            require(amount.msat > 0) { "Amount must be greater than zero." }
        }

        val stateFlow = MutableStateFlow<PayInvoiceRequestState>(PayInvoiceRequestState.Loading)

        val job = scope.launch {
            try {
                val paidInvoice = payInvoice(
                    invoice = invoice,
                    amount = amount
                )
                stateFlow.value = PayInvoiceRequestState.Success(paidInvoice)
            } catch (e: kotlinx.coroutines.CancellationException) {
                stateFlow.value = PayInvoiceRequestState.Failure(
                    error = AppError.Unexpected("Payment cancelled")
                )
                throw e
            } catch (e: AppErrorException) {
                stateFlow.value = PayInvoiceRequestState.Failure(error = e.error)
            } catch (e: Throwable) {
                stateFlow.value = PayInvoiceRequestState.Failure(
                    error = AppError.Unexpected(e.message)
                )
            }
        }

        return object : PayInvoiceRequest {
            override val state = stateFlow
            override fun cancel() {
                job.cancel()
            }
        }
    }

    override suspend fun lookupPayment(paymentHash: ByteVector32): PaymentLookupResult {
        if (!networkConnectivity.isNetworkAvailable()) {
            return PaymentLookupResult.LookupError(AppError.NetworkUnavailable)
        }

        return try {
            val client = connectionManager.getClient()
            val result = client.lookupInvoice(
                params = LookupInvoiceParams(paymentHash = paymentHash.toHex()),
                timeoutMs = LOOKUP_TIMEOUT_MILLIS
            )

            when (result) {
                is NwcResult.Success -> {
                    val tx = result.value
                    when (tx.state) {
                        TransactionState.SETTLED -> PaymentLookupResult.Settled(
                            PaidInvoice(
                                preimage = tx.preimage.toByteVector32OrNull(),
                                feesPaid = tx.feesPaid?.msats?.msat
                            )
                        )

                        TransactionState.PENDING -> PaymentLookupResult.Pending

                        TransactionState.FAILED, TransactionState.EXPIRED ->
                            PaymentLookupResult.Failed

                        null -> {
                            // State is null - infer from other fields
                            if (tx.settledAt != null || tx.preimage != null) {
                                PaymentLookupResult.Settled(
                                    PaidInvoice(
                                        preimage = tx.preimage.toByteVector32OrNull(),
                                        feesPaid = tx.feesPaid?.msats?.msat
                                    )
                                )
                            } else {
                                PaymentLookupResult.Pending
                            }
                        }
                    }
                }

                is NwcResult.Failure -> {
                    when (val error = result.error) {
                        is NwcError.WalletError -> {
                            if (error.code.code == "NOT_FOUND") {
                                PaymentLookupResult.NotFound
                            } else {
                                PaymentLookupResult.LookupError(error.toAppError())
                            }
                        }

                        is NwcError.ConnectionError -> PaymentLookupResult.LookupError(
                            AppError.RelayConnectionFailed(error.message)
                        )

                        is NwcError.Timeout -> PaymentLookupResult.LookupError(AppError.Timeout)

                        else -> PaymentLookupResult.LookupError(error.toAppError())
                    }
                }
            }
        } catch (e: AppErrorException) {
            PaymentLookupResult.LookupError(e.error)
        }
    }
}

private fun String?.toByteVector32OrNull(): ByteVector32? =
    this?.let { value -> runCatching { ByteVector32.fromValidHex(value) }.getOrNull() }
