package xyz.lilsus.papp.data.blink

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.repository.PaymentProvider

/**
 * Payment provider implementation for Blink wallets.
 * Routes payments through the Blink GraphQL API.
 */
class BlinkPaymentRepository(
    private val apiClient: BlinkApiClient,
    private val credentialStore: BlinkCredentialStore,
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

        val job = scope.launch {
            try {
                val result = payInvoice(invoice, amountMsats)
                stateFlow.value = PayInvoiceRequestState.Success(result)
            } catch (e: AppErrorException) {
                stateFlow.value = PayInvoiceRequestState.Failure(e.error)
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

    override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
        val walletId = activeWalletId
            ?: throw AppErrorException(AppError.MissingWalletConnection)

        val apiKey = credentialStore.getApiKey(walletId)
            ?: throw AppErrorException(AppError.AuthenticationFailure("API key not found"))

        val blinkWalletId = apiClient.fetchDefaultWalletId(apiKey)

        val result = if (amountMsats != null) {
            // Zero-amount invoice - convert msats to sats
            val amountSats = (amountMsats + 999) / 1000 // Round up to nearest sat
            apiClient.payNoAmountInvoice(apiKey, blinkWalletId, invoice, amountSats)
        } else {
            // Invoice with embedded amount
            apiClient.payInvoice(apiKey, blinkWalletId, invoice)
        }

        return PaidInvoice(
            // Blink doesn't return preimage in the simple payment flow
            preimage = "",
            feesPaidMsats = result.feesPaidMsats
        )
    }
}
