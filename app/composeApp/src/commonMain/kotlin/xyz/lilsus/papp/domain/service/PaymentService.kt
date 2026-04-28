package xyz.lilsus.papp.domain.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xyz.lilsus.papp.data.blink.BlinkPaymentRepository
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.model.PaymentLookupResult
import xyz.lilsus.papp.domain.model.WalletConnection
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

    // NOTE: This synchronous seed closes the startup routing race. If repository reads ever become
    // blocking I/O, revisit this to avoid constructor-time thread blocking.
    private val currentConnection = MutableStateFlow<WalletConnection?>(
        runBlocking { walletSettingsRepository.getWalletConnection() }
    )

    init {
        val initialBlinkWalletPublicKey = currentConnection.value
            ?.takeIf { connection -> connection.isBlink }
            ?.walletPublicKey
        blinkRepository.setActiveWallet(initialBlinkWalletPublicKey)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            walletSettingsRepository.walletConnection.collectLatest { connection ->
                currentConnection.value = connection
                if (connection?.isBlink == true) {
                    blinkRepository.setActiveWallet(connection.walletPublicKey)
                } else {
                    blinkRepository.setActiveWallet(null)
                }
            }
        }
    }

    fun startPayInvoiceRequest(invoice: String, amountMsats: Long? = null): PayInvoiceRequest =
        startPayInvoiceRequest(
            invoice = invoice,
            amountMsats = amountMsats,
            walletUri = null,
            walletType = null
        )

    override fun startPayInvoiceRequest(
        invoice: String,
        amountMsats: Long?,
        walletUri: String?,
        walletType: WalletType?
    ): PayInvoiceRequest {
        val connection = currentConnection.value
        val targetWalletUri = walletUri ?: connection.toPaymentWalletUri()
        return when (walletType ?: connection?.type) {
            WalletType.NWC -> nwcRepository.startPayInvoiceRequest(
                invoice = invoice,
                amountMsats = amountMsats,
                walletUri = targetWalletUri,
                walletType = WalletType.NWC
            )

            WalletType.BLINK -> blinkRepository.startPayInvoiceRequest(
                invoice = invoice,
                amountMsats = amountMsats,
                walletUri = targetWalletUri,
                walletType = WalletType.BLINK
            )

            null -> createMissingWalletRequest()
        }
    }

    suspend fun payInvoice(invoice: String, amountMsats: Long? = null): PaidInvoice = payInvoice(
        invoice = invoice,
        amountMsats = amountMsats,
        walletUri = null,
        walletType = null
    )

    override suspend fun payInvoice(
        invoice: String,
        amountMsats: Long?,
        walletUri: String?,
        walletType: WalletType?
    ): PaidInvoice {
        val connection = currentConnection.value
        val targetWalletUri = walletUri ?: connection.toPaymentWalletUri()
        return when (walletType ?: connection?.type) {
            WalletType.NWC -> nwcRepository.payInvoice(
                invoice = invoice,
                amountMsats = amountMsats,
                walletUri = targetWalletUri,
                walletType = WalletType.NWC
            )

            WalletType.BLINK -> blinkRepository.payInvoice(
                invoice = invoice,
                amountMsats = amountMsats,
                walletUri = targetWalletUri,
                walletType = WalletType.BLINK
            )

            null -> throw AppErrorException(AppError.MissingWalletConnection)
        }
    }

    override suspend fun lookupPayment(
        paymentHash: String,
        walletUri: String?,
        walletType: WalletType?
    ): PaymentLookupResult {
        // Use provided wallet type for routing, or fall back to current wallet type
        val effectiveType = walletType ?: currentConnection.value?.type
        return when (effectiveType) {
            WalletType.NWC -> nwcRepository.lookupPayment(paymentHash, walletUri, walletType)
            WalletType.BLINK -> blinkRepository.lookupPayment(paymentHash, walletUri, walletType)
            null -> PaymentLookupResult.LookupError(AppError.MissingWalletConnection)
        }
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

private fun WalletConnection?.toPaymentWalletUri(): String? = when (this?.type) {
    WalletType.BLINK -> walletPublicKey
    WalletType.NWC -> uri.ifBlank { null }
    null -> null
}
