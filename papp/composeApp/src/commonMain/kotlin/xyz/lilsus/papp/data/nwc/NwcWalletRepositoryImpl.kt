package xyz.lilsus.papp.data.nwc

import io.github.nicolals.nwc.Amount
import io.github.nicolals.nwc.LookupInvoiceParams
import io.github.nicolals.nwc.NwcClient
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
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository
import xyz.lilsus.papp.platform.NetworkConnectivity

/**
 * [NwcWalletRepository] implementation backed by [NwcClient].
 *
 * Uses [NwcConnectionManager] to obtain persistent clients, preventing
 * connection thrashing and ensuring reliable background cleanup.
 */
class NwcWalletRepositoryImpl(
    private val walletSettingsRepository: WalletSettingsRepository,
    private val connectionManager: NwcConnectionManager,
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

        val client = getClient()
        val result = client.payInvoice(
            invoice = invoice,
            amount = amountMsats?.let { Amount.fromMsats(it) },
            timeoutMs = payTimeoutMillis,
            verifyOnTimeout = true
        )

        return when (result) {
            is NwcResult.Success -> PaidInvoice(
                preimage = result.value.preimage,
                feesPaidMsats = result.value.feesPaid?.msats
            )

            is NwcResult.Failure -> throw result.error.toAppErrorException()
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

    override suspend fun lookupPayment(
        paymentHash: String,
        walletUri: String?,
        walletType: WalletType?
    ): PaymentLookupResult {
        require(paymentHash.isNotBlank()) { "Payment hash must not be blank." }

        if (!networkConnectivity.isNetworkAvailable()) {
            return PaymentLookupResult.LookupError(AppError.NetworkUnavailable)
        }

        return try {
            val client = getClient(walletUri)
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

    private suspend fun getClient(specificWalletUri: String? = null): NwcClient {
        val connection = if (specificWalletUri != null) {
            // Find wallet by URI
            walletSettingsRepository.getWallets()
                .firstOrNull { it.uri == specificWalletUri }
                ?: throw AppErrorException(AppError.MissingWalletConnection)
        } else {
            null
        }

        return connectionManager.getClient(connection)
    }
}
