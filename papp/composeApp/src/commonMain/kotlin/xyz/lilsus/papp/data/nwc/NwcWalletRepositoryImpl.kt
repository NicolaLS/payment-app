package xyz.lilsus.papp.data.nwc

import io.github.nicolals.nwc.Amount
import io.github.nicolals.nwc.LookupInvoiceParams
import io.github.nicolals.nwc.NwcClient
import io.github.nicolals.nwc.NwcClientState
import io.github.nicolals.nwc.NwcError
import io.github.nicolals.nwc.NwcResult
import io.github.nicolals.nwc.TransactionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.platform.NetworkConnectivity

/**
 * [NwcWalletRepository] implementation backed by [NwcClient].
 *
 * Creates a fresh client connection for each operation, ensuring reliability
 * without the complexity of managing cached websocket state. This approach
 * eliminates issues with stale connections (especially on iOS where background
 * suspension leaves websockets in half-open states).
 *
 * Tradeoff: Slightly slower for rapid successive operations, but more reliable
 * and much simpler. The primary use case (single payment) has optimal UX.
 */
class NwcWalletRepositoryImpl(
    private val walletSettingsRepository: WalletSettingsRepository,
    private val clientFactory: NwcClientFactory,
    private val scope: CoroutineScope,
    private val networkConnectivity: NetworkConnectivity,
    private val payTimeoutMillis: Long = DEFAULT_NWC_PAY_TIMEOUT_MILLIS
) : NwcWalletRepository {

    override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
        require(invoice.isNotBlank()) { "Invoice must not be blank." }
        if (amountMsats != null) {
            require(amountMsats > 0) { "Amount must be greater than zero." }
        }

        if (!networkConnectivity.isNetworkAvailable()) {
            throw AppErrorException(AppError.NetworkUnavailable)
        }

        return withFreshClient { client ->
            val result = client.payInvoice(
                invoice = invoice,
                amount = amountMsats?.let { Amount.fromMsats(it) },
                timeoutMs = payTimeoutMillis,
                verifyOnTimeout = true
            )

            when (result) {
                is NwcResult.Success -> PaidInvoice(
                    preimage = result.value.preimage,
                    feesPaidMsats = result.value.feesPaid?.msats
                )

                is NwcResult.Failure -> throw result.error.toAppErrorException()
            }
        }
    }

    override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?): PayInvoiceRequest {
        require(invoice.isNotBlank()) { "Invoice must not be blank." }
        if (amountMsats != null) {
            require(amountMsats > 0) { "Amount must be greater than zero." }
        }

        val stateFlow = MutableStateFlow<PayInvoiceRequestState>(PayInvoiceRequestState.Loading)

        val job = scope.launch {
            try {
                val paidInvoice = payInvoice(invoice, amountMsats)
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

    override suspend fun lookupPayment(paymentHash: String): PaymentLookupResult {
        require(paymentHash.isNotBlank()) { "Payment hash must not be blank." }

        if (!networkConnectivity.isNetworkAvailable()) {
            return PaymentLookupResult.LookupError(AppError.NetworkUnavailable)
        }

        return try {
            withFreshClient { client ->
                val result = client.lookupInvoice(
                    params = LookupInvoiceParams(paymentHash = paymentHash),
                    timeoutMs = LOOKUP_TIMEOUT_MILLIS
                )

                when (result) {
                    is NwcResult.Success -> {
                        val tx = result.value
                        when (tx.state) {
                            TransactionState.SETTLED -> PaymentLookupResult.Settled(
                                PaidInvoice(
                                    preimage = tx.preimage,
                                    feesPaidMsats = tx.feesPaid?.msats
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
                                            preimage = tx.preimage,
                                            feesPaidMsats = tx.feesPaid?.msats
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
                                AppError.NetworkUnavailable
                            )

                            is NwcError.Timeout -> PaymentLookupResult.LookupError(AppError.Timeout)

                            else -> PaymentLookupResult.LookupError(error.toAppError())
                        }
                    }
                }
            }
        } catch (e: AppErrorException) {
            PaymentLookupResult.LookupError(e.error)
        }
    }

    /**
     * Executes [block] with a fresh NWC client connection.
     * The client is always closed after the operation completes.
     */
    private suspend fun <T> withFreshClient(block: suspend (NwcClient) -> T): T {
        val connection = walletSettingsRepository.getWalletConnection()
            ?: throw AppErrorException(AppError.MissingWalletConnection)

        val client = clientFactory.create(connection)
        try {
            client.connect()

            if (!client.awaitReady(timeoutMs = DEFAULT_NWC_REQUEST_TIMEOUT_MILLIS)) {
                val state = client.state.value
                if (state is NwcClientState.Failed) {
                    throw state.error.toAppErrorException()
                }
                throw AppErrorException(AppError.NetworkUnavailable)
            }

            return block(client)
        } finally {
            client.close()
        }
    }
}
