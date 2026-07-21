package xyz.lilsus.papp.domain.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.BlinkWalletRepository
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.PaymentProvider
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/** Routes payment operations to the connected NWC or Blink wallet. */
class PaymentService(
    private val walletSettingsRepository: WalletSettingsRepository,
    private val nwcRepository: NwcWalletRepository,
    private val blinkRepository: BlinkWalletRepository,
    scope: CoroutineScope
) : PaymentProvider {

    // Seed synchronously so a payment requested during startup cannot race the settings collector.
    private val currentConnection = MutableStateFlow<WalletConnection?>(
        runBlocking { walletSettingsRepository.getWalletConnection() }
    )

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            walletSettingsRepository.walletConnection.collectLatest { connection ->
                currentConnection.value = connection
            }
        }
    }

    override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?): PayInvoiceRequest =
        when (currentConnection.value) {
            null -> createMissingWalletRequest()

            else -> if (currentConnection.value?.isNwc == true) {
                nwcRepository.startPayInvoiceRequest(invoice, amountMsats)
            } else {
                blinkRepository.startPayInvoiceRequest(invoice, amountMsats)
            }
        }

    override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice =
        when (currentConnection.value) {
            null -> throw AppErrorException(AppError.MissingWalletConnection)

            else -> if (currentConnection.value?.isNwc == true) {
                nwcRepository.payInvoice(invoice, amountMsats)
            } else {
                blinkRepository.payInvoice(invoice, amountMsats)
            }
        }

    override suspend fun lookupPayment(paymentHash: String): PaymentLookupResult =
        when (currentConnection.value) {
            null -> PaymentLookupResult.LookupError(AppError.MissingWalletConnection)

            else -> if (currentConnection.value?.isNwc == true) {
                nwcRepository.lookupPayment(paymentHash)
            } else {
                blinkRepository.lookupPayment(paymentHash)
            }
        }

    private fun createMissingWalletRequest(): PayInvoiceRequest {
        val stateFlow = MutableStateFlow<PayInvoiceRequestState>(
            PayInvoiceRequestState.Failure(AppError.MissingWalletConnection)
        )
        return object : PayInvoiceRequest {
            override val state = stateFlow
            override fun cancel() = Unit
        }
    }
}
