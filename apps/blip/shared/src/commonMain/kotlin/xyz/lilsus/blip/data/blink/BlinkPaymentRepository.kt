package xyz.lilsus.blip.data.blink

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.MilliSatoshi
import fr.acinq.lightning.payment.Bolt11Invoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.blip.domain.model.AppError
import xyz.lilsus.blip.domain.model.AppErrorException
import xyz.lilsus.blip.domain.model.BlinkErrorType
import xyz.lilsus.blip.domain.model.PaidInvoice
import xyz.lilsus.blip.domain.model.PayInvoiceRequest
import xyz.lilsus.blip.domain.model.PayInvoiceRequestState
import xyz.lilsus.blip.domain.model.PaymentLookupResult
import xyz.lilsus.blip.domain.repository.BlinkWalletRepository
import xyz.lilsus.blip.domain.repository.WalletSettingsRepository
import xyz.lilsus.blip.platform.NetworkConnectivity

/**
 * Payment provider implementation for Blink wallets.
 * Routes payments through the Blink GraphQL API.
 */
class BlinkPaymentRepository(
    private val apiClient: BlinkApiClient,
    private val credentialStore: BlinkCredentialStore,
    private val walletSettingsRepository: WalletSettingsRepository,
    private val networkConnectivity: NetworkConnectivity,
    private val scope: CoroutineScope
) : BlinkWalletRepository {

    override fun startPayInvoiceRequest(
        invoice: Bolt11Invoice,
        amount: MilliSatoshi?
    ): PayInvoiceRequest {
        if (amount != null) {
            require(amount.msat > 0) { "Amount must be greater than zero" }
        }

        val stateFlow = MutableStateFlow<PayInvoiceRequestState>(PayInvoiceRequestState.Loading)

        val job = scope.launch {
            try {
                val result = payInvoice(
                    invoice = invoice,
                    amount = amount
                )
                stateFlow.value = PayInvoiceRequestState.Success(result)
            } catch (e: AppErrorException) {
                val finalError = handlePotentialAuthError(e.error)
                stateFlow.value = PayInvoiceRequestState.Failure(finalError)
            } catch (e: Exception) {
                stateFlow.value = PayInvoiceRequestState.Failure(
                    AppError.Unexpected(e.message)
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

    /**
     * Handles authentication errors by auto-removing the wallet.
     * When an API key is invalid or revoked, removes the wallet and API key,
     * then returns an error indicating the wallet was removed.
     *
     */
    private suspend fun handlePotentialAuthError(error: AppError): AppError {
        if (error !is AppError.BlinkError || error.type != BlinkErrorType.InvalidApiKey) {
            return error
        }

        walletSettingsRepository.clearWalletConnection()

        return AppError.BlinkError(BlinkErrorType.InvalidApiKeyWalletRemoved)
    }

    override suspend fun payInvoice(invoice: Bolt11Invoice, amount: MilliSatoshi?): PaidInvoice {
        if (!networkConnectivity.isNetworkAvailable()) {
            throw AppErrorException(AppError.NetworkUnavailable)
        }

        requireBlinkConnection()

        val apiKey = credentialStore.getApiKey()
            ?: throw AppErrorException(
                AppError.AuthenticationFailure(
                    "API key not found. Please reconnect your Blink wallet."
                )
            )

        val blinkWalletId = credentialStore.getDefaultWalletId()
            ?: apiClient.fetchDefaultWalletId(apiKey).also {
                credentialStore.storeDefaultWalletId(it)
            }

        val result = try {
            if (amount != null) {
                // Zero-amount invoice - convert msats to sats
                val amountSats = (amount.msat + 999) / 1000 // Round up to nearest sat
                apiClient.payNoAmountInvoice(apiKey, blinkWalletId, invoice.write(), amountSats)
            } else {
                // Invoice with embedded amount
                apiClient.payInvoice(apiKey, blinkWalletId, invoice.write())
            }
        } catch (e: AppErrorException) {
            val mappedError = when (e.error) {
                AppError.NetworkUnavailable,
                AppError.Timeout ->
                    AppError.PaymentUnconfirmed(
                        paymentHash = null, // Caller has payment hash from invoice
                        message = "Payment status unknown"
                    )

                else -> null
            }
            if (mappedError != null) {
                throw AppErrorException(mappedError, e)
            }
            throw e
        }

        // Handle PENDING status - payment is in-flight but not yet confirmed
        if (result is BlinkPaymentResult.Pending) {
            throw AppErrorException(
                AppError.PaymentUnconfirmed(
                    paymentHash = null, // Caller has payment hash from invoice
                    message = "Payment is being processed"
                )
            )
        }

        val wasAlreadyPaid = result is BlinkPaymentResult.AlreadyPaid

        return PaidInvoice(
            preimage = result.preimage,
            feesPaid = if (wasAlreadyPaid) null else result.feesPaid,
            wasAlreadyPaid = wasAlreadyPaid
        )
    }

    override suspend fun lookupPayment(paymentHash: ByteVector32): PaymentLookupResult {
        if (!networkConnectivity.isNetworkAvailable()) {
            return PaymentLookupResult.LookupError(AppError.NetworkUnavailable)
        }

        val wallet = walletSettingsRepository.getWalletConnection()
        if (wallet?.isBlink != true) {
            return PaymentLookupResult.LookupError(AppError.MissingWalletConnection)
        }

        val apiKey = credentialStore.getApiKey()
            ?: return PaymentLookupResult.LookupError(
                AppError.AuthenticationFailure("API key not found")
            )

        return try {
            when (val status = apiClient.lookupPaymentStatus(apiKey, paymentHash.toHex())) {
                is BlinkPaymentStatusResult.Paid -> PaymentLookupResult.Settled(
                    PaidInvoice(
                        preimage = status.preimage,
                        feesPaid = status.feesPaid
                    )
                )

                BlinkPaymentStatusResult.Pending -> PaymentLookupResult.Pending

                BlinkPaymentStatusResult.Failed -> PaymentLookupResult.Failed

                BlinkPaymentStatusResult.NotFound -> PaymentLookupResult.NotFound
            }
        } catch (e: AppErrorException) {
            PaymentLookupResult.LookupError(e.error)
        }
    }

    private suspend fun requireBlinkConnection() {
        if (walletSettingsRepository.getWalletConnection()?.isBlink != true) {
            throw AppErrorException(AppError.MissingWalletConnection)
        }
    }
}
