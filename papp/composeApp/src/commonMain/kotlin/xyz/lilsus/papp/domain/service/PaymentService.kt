package xyz.lilsus.papp.domain.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import xyz.lilsus.papp.data.blink.BlinkPaymentRepository
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.WalletType
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
    private val blinkRepository: BlinkPaymentRepository,
    scope: CoroutineScope
) : PaymentProvider {

    private var currentWalletType: WalletType? = null

    init {
        scope.launch {
            walletSettingsRepository.walletConnection.collectLatest { connection ->
                currentWalletType = connection?.type
                if (connection?.isBlink == true) {
                    blinkRepository.setActiveWallet(connection.walletPublicKey)
                } else {
                    blinkRepository.setActiveWallet(null)
                }
            }
        }
    }

    override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?): PayInvoiceRequest =
        when (currentWalletType) {
            WalletType.NWC -> nwcRepository.startPayInvoiceRequest(invoice, amountMsats)
            WalletType.BLINK -> blinkRepository.startPayInvoiceRequest(invoice, amountMsats)
            null -> createMissingWalletRequest()
        }

    override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice =
        when (currentWalletType) {
            WalletType.NWC -> nwcRepository.payInvoice(invoice, amountMsats)
            WalletType.BLINK -> blinkRepository.payInvoice(invoice, amountMsats)
            null -> throw AppErrorException(AppError.MissingWalletConnection)
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
