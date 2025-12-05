package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcWalletContract
import io.github.nostr.nwc.model.BitcoinAmount
import io.github.nostr.nwc.model.NwcRequestState
import io.github.nostr.nwc.model.PayInvoiceParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.PaidInvoice
import xyz.lilsus.papp.domain.model.PayInvoiceRequest
import xyz.lilsus.papp.domain.model.PayInvoiceRequestState
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/**
 * Default [NwcWalletRepository] implementation backed by [NwcWalletContract].
 *
 * The repository lazily creates and caches an [NwcWalletContract] bound to the
 * current wallet-connect URI provided by [walletSettingsRepository].
 *
 * All request methods return immediately with Loading state - client
 * initialization and requests happen in the background.
 */
class NwcWalletRepositoryImpl(
    private val walletSettingsRepository: WalletSettingsRepository,
    private val walletFactory: NwcWalletFactory,
    private val scope: CoroutineScope,
    private val payTimeoutMillis: Long = DEFAULT_NWC_PAY_TIMEOUT_MILLIS
) : NwcWalletRepository {

    private val walletMutex = Mutex()
    private var cachedWallet: NwcWalletContract? = null
    private var activeUri: String? = null

    init {
        scope.launch {
            walletSettingsRepository.walletConnection.collectLatest { connection ->
                if (connection == null) {
                    // Close wallet when connection is cleared
                    closeWallet()
                    return@collectLatest
                }
                // Close wallet if switched to a different one
                walletMutex.withLock {
                    if (activeUri != null && activeUri != connection.uri) {
                        closeWalletLocked()
                    }
                }
            }
        }
    }

    override suspend fun payInvoice(invoice: String, amountMsats: Long?): PaidInvoice {
        val request = startPayInvoiceRequest(invoice = invoice, amountMsats = amountMsats)
        try {
            val state = withTimeoutOrNull(payTimeoutMillis) {
                request.state.first {
                    it is PayInvoiceRequestState.Success || it is PayInvoiceRequestState.Failure
                }
            }
            return when (state) {
                is PayInvoiceRequestState.Success -> state.invoice
                is PayInvoiceRequestState.Failure -> throw AppErrorException(state.error)
                null, PayInvoiceRequestState.Loading -> throw AppErrorException(AppError.Timeout)
            }
        } finally {
            request.cancel()
        }
    }

    override fun startPayInvoiceRequest(invoice: String, amountMsats: Long?): PayInvoiceRequest {
        require(invoice.isNotBlank()) { "Invoice must not be blank." }
        if (amountMsats != null) {
            require(amountMsats > 0) { "Amount must be greater than zero." }
        }

        // Create app-level request that wraps the NwcWallet request
        val stateFlow = MutableStateFlow<PayInvoiceRequestState>(PayInvoiceRequestState.Loading)

        // Hold reference to underlying NWC request for proper cancellation
        var nwcRequest: io.github.nostr.nwc.NwcRequest<*>? = null

        val job = scope.launch {
            try {
                val wallet = ensureWallet()
                    ?: run {
                        stateFlow.value =
                            PayInvoiceRequestState.Failure(AppError.MissingWalletConnection)
                        return@launch
                    }

                nwcRequest = wallet.payInvoice(
                    PayInvoiceParams(
                        invoice = invoice,
                        amount = amountMsats?.let(BitcoinAmount::fromMsats)
                    )
                )

                // Forward NWC request state to app state
                nwcRequest!!.state.collect { nwcState ->
                    stateFlow.value = when (nwcState) {
                        NwcRequestState.Loading -> PayInvoiceRequestState.Loading

                        is NwcRequestState.Success -> PayInvoiceRequestState.Success(
                            invoice = PaidInvoice(
                                preimage = nwcState.value.preimage,
                                feesPaidMsats = nwcState.value.feesPaid?.msats
                            )
                        )

                        is NwcRequestState.Failure -> PayInvoiceRequestState.Failure(
                            error = nwcState.failure.toAppError()
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
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
                nwcRequest?.cancel()
                job.cancel()
            }
        }
    }

    private suspend fun ensureWallet(): NwcWalletContract? {
        val connection = walletSettingsRepository.getWalletConnection() ?: return null
        val uri = connection.uri

        // Fast path
        cachedWallet?.takeIf { activeUri == uri }?.let { return it }

        return walletMutex.withLock {
            // Double-check inside lock
            cachedWallet?.takeIf { activeUri == uri }?.let { return@withLock it }

            // Close existing wallet if URI changed
            if (activeUri != null && activeUri != uri) {
                closeWalletLocked()
            }

            // Create new wallet (returns immediately, init is deferred)
            val wallet = walletFactory.create(connection)
            cachedWallet = wallet
            activeUri = uri
            wallet
        }
    }

    private suspend fun closeWallet() {
        walletMutex.withLock { closeWalletLocked() }
    }

    private suspend fun closeWalletLocked() {
        val previous = cachedWallet
        cachedWallet = null
        activeUri = null
        previous?.close()
    }
}
