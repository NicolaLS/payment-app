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
import xyz.lilsus.papp.domain.model.WalletPaymentTarget
import xyz.lilsus.papp.domain.model.toPaymentTarget
import xyz.lilsus.papp.domain.repository.BlinkWalletRepository
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.PaymentProvider
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/**
 * Unified payment service that routes payments to the appropriate provider
 * based on the active wallet type (NWC or Blink).
 *
 * This service maintains wallet type awareness and delegates to the correct
 * payment provider without exposing implementation details to callers.
 */
class PaymentService(
    private val walletSettingsRepository: WalletSettingsRepository,
    private val nwcRepository: NwcWalletRepository,
    private val blinkRepository: BlinkWalletRepository,
    scope: CoroutineScope
) : PaymentProvider {

    // NOTE: This synchronous seed closes the startup routing race. If repository reads ever become
    // blocking I/O, revisit this to avoid constructor-time thread blocking.
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

    fun startPayInvoiceRequest(invoice: String, amountMsats: Long? = null): PayInvoiceRequest =
        startPayInvoiceRequest(
            invoice = invoice,
            amountMsats = amountMsats,
            walletTarget = null
        )

    override fun startPayInvoiceRequest(
        invoice: String,
        amountMsats: Long?,
        walletTarget: WalletPaymentTarget?
    ): PayInvoiceRequest {
        val connection = currentConnection.value
        return when (val target = walletTarget ?: connection?.toPaymentTarget()) {
            is WalletPaymentTarget.Nwc -> nwcRepository.startPayInvoiceRequest(
                invoice = invoice,
                amountMsats = amountMsats,
                walletTarget = target
            )

            is WalletPaymentTarget.Blink -> blinkRepository.startPayInvoiceRequest(
                invoice = invoice,
                amountMsats = amountMsats,
                walletTarget = target
            )

            null -> createMissingWalletRequest()
        }
    }

    suspend fun payInvoice(invoice: String, amountMsats: Long? = null): PaidInvoice = payInvoice(
        invoice = invoice,
        amountMsats = amountMsats,
        walletTarget = null
    )

    override suspend fun payInvoice(
        invoice: String,
        amountMsats: Long?,
        walletTarget: WalletPaymentTarget?
    ): PaidInvoice {
        val connection = currentConnection.value
        return when (val target = walletTarget ?: connection?.toPaymentTarget()) {
            is WalletPaymentTarget.Nwc -> nwcRepository.payInvoice(
                invoice = invoice,
                amountMsats = amountMsats,
                walletTarget = target
            )

            is WalletPaymentTarget.Blink -> blinkRepository.payInvoice(
                invoice = invoice,
                amountMsats = amountMsats,
                walletTarget = target
            )

            null -> throw AppErrorException(AppError.MissingWalletConnection)
        }
    }

    override suspend fun lookupPayment(
        paymentHash: String,
        walletTarget: WalletPaymentTarget?
    ): PaymentLookupResult =
        when (val target = walletTarget ?: currentConnection.value?.toPaymentTarget()) {
            is WalletPaymentTarget.Nwc -> nwcRepository.lookupPayment(paymentHash, target)
            is WalletPaymentTarget.Blink -> blinkRepository.lookupPayment(paymentHash, target)
            null -> PaymentLookupResult.LookupError(AppError.MissingWalletConnection)
        }

    private fun createMissingWalletRequest(): PayInvoiceRequest {
        val stateFlow = MutableStateFlow<PayInvoiceRequestState>(
            PayInvoiceRequestState.Failure(AppError.MissingWalletConnection)
        )
        return object : PayInvoiceRequest {
            override val state = stateFlow
            override fun cancel() { /* No-op */ }
        }
    }
}
