package xyz.lilsus.papp.data.blink

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.BlinkErrorType
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.domain.repository.PaymentProvider
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/**
 * Payment provider implementation for Blink wallets.
 * Routes payments through the Blink GraphQL API.
 */
class BlinkPaymentRepository(
    private val apiClient: BlinkApiClient,
    private val credentialStore: BlinkCredentialStore,
    private val walletSettingsRepository: WalletSettingsRepository,
    private val scope: CoroutineScope
) : PaymentProvider {

    private var activeWalletId: String? = null

    /**
     * Sets the active Blink wallet ID for payments.
     * Must be called before making payments.
     */
    fun setActiveWallet(walletId: String?) {
        activeWalletId = walletId
    }

    override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?): PayInvoiceRequest {
        require(invoice.isNotBlank()) { "Invoice must not be blank" }
        if (amountMsats != null) {
            require(amountMsats > 0) { "Amount must be greater than zero" }
        }

        val stateFlow = MutableStateFlow<PayInvoiceRequestState>(PayInvoiceRequestState.Loading)

        // Capture wallet ID at request time to avoid race conditions if user switches wallets
        val walletIdAtRequestTime = activeWalletId

        val job = scope.launch {
            try {
                val result = payInvoice(invoice, amountMsats)
                stateFlow.value = PayInvoiceRequestState.Success(result)
            } catch (e: AppErrorException) {
                val finalError = handlePotentialAuthError(e.error, walletIdAtRequestTime)
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
     * @param walletId The wallet ID that was used for this payment request.
     */
    private suspend fun handlePotentialAuthError(error: AppError, walletId: String?): AppError {
        if (error !is AppError.BlinkError || error.type != BlinkErrorType.InvalidApiKey) {
            return error
        }

        if (walletId == null) return error

        // Remove the wallet and API key
        credentialStore.removeApiKey(walletId)
        walletSettingsRepository.removeWallet(walletId)

        return AppError.BlinkError(BlinkErrorType.InvalidApiKeyWalletRemoved)
    }

    override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
        val walletId = activeWalletId
            ?: throw AppErrorException(AppError.MissingWalletConnection)

        val apiKey = credentialStore.getApiKey(walletId)
            ?: throw AppErrorException(
                AppError.AuthenticationFailure(
                    "API key not found. Please reconnect your Blink wallet."
                )
            )

        val blinkWalletId = apiClient.fetchDefaultWalletId(apiKey)

        val result = if (amountMsats != null) {
            // Zero-amount invoice - convert msats to sats
            val amountSats = (amountMsats + 999) / 1000 // Round up to nearest sat
            apiClient.payNoAmountInvoice(apiKey, blinkWalletId, invoice, amountSats)
        } else {
            // Invoice with embedded amount
            apiClient.payInvoice(apiKey, blinkWalletId, invoice)
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

        return PaidInvoice(
            preimage = null, // Blink doesn't return preimage in the simple payment flow
            feesPaidMsats = result.feesPaidMsats
        )
    }

    override suspend fun lookupPayment(
        paymentHash: String,
        walletUri: String?,
        walletType: WalletType?
    ): PaymentLookupResult {
        // Use provided wallet ID or fall back to active wallet
        // For Blink, walletUri is the wallet's public key (ID)
        val walletId = walletUri ?: activeWalletId
            ?: return PaymentLookupResult.LookupError(AppError.MissingWalletConnection)

        val apiKey = credentialStore.getApiKey(walletId)
            ?: return PaymentLookupResult.LookupError(
                AppError.AuthenticationFailure("API key not found")
            )

        return try {
            when (apiClient.lookupPaymentStatus(apiKey, paymentHash)) {
                BlinkPaymentStatusResult.Paid -> PaymentLookupResult.Settled(
                    PaidInvoice(preimage = null, feesPaidMsats = null)
                )

                BlinkPaymentStatusResult.Pending -> PaymentLookupResult.Pending

                BlinkPaymentStatusResult.Failed -> PaymentLookupResult.Failed

                BlinkPaymentStatusResult.NotFound -> PaymentLookupResult.NotFound
            }
        } catch (e: AppErrorException) {
            PaymentLookupResult.LookupError(e.error)
        }
    }
}
