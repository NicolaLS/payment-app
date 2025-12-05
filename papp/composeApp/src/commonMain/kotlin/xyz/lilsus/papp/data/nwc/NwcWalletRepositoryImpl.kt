package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.model.BitcoinAmount
import io.github.nostr.nwc.model.NwcResult
import io.github.nostr.nwc.model.PayInvoiceParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.repository.NwcWalletRepository
import xyz.lilsus.papp.domain.repository.WalletSettingsRepository

/**
 * Default [NwcWalletRepository] implementation backed by the nwc-kmp client.
 *
 * The repository lazily instantiates and caches an [NwcClientHandle] bound to the
 * current wallet-connect URI provided by [walletSettingsRepository].
 */
class NwcWalletRepositoryImpl(
    private val walletSettingsRepository: WalletSettingsRepository,
    private val clientFactory: NwcClientFactory,
    private val scope: CoroutineScope,
    private val payTimeoutMillis: Long = DEFAULT_NWC_PAY_TIMEOUT_MILLIS
) : NwcWalletRepository {

    private val clientMutex = Mutex()
    private var cachedHandle: NwcClientHandle? = null
    private var inFlightHandle: Deferred<NwcClientHandle>? = null
    private var activeUri: String? = null

    init {
        scope.launch {
            walletSettingsRepository.walletConnection.collectLatest { connection ->
                if (connection == null) {
                    // Close any cached handle when wallet is cleared to release resources
                    closeCachedHandle()
                    return@collectLatest
                }
                // Close cached handle if wallet switched to a different one
                clientMutex.withLock {
                    if (cachedHandle?.uri != null && cachedHandle?.uri != connection.uri) {
                        closeCachedHandleLocked()
                    }
                }
                // Client will be created on-demand when payInvoice() is called
                // This avoids 0-8s startup latency from eager handshake
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

    override suspend fun startPayInvoiceRequest(
        invoice: String,
        amountMsats: Long?
    ): PayInvoiceRequest {
        require(invoice.isNotBlank()) { "Invoice must not be blank." }
        if (amountMsats != null) {
            require(amountMsats > 0) { "Amount must be greater than zero." }
        }

        val connection = walletSettingsRepository.getWalletConnection()
            ?: throw AppErrorException(AppError.MissingWalletConnection)

        val handle = ensureClient(connection)
        val request = try {
            handle.client.payInvoiceRequest(
                params = PayInvoiceParams(
                    invoice = invoice,
                    amount = amountMsats?.let(BitcoinAmount::fromMsats)
                )
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            invalidateHandle(handle)
            throw error
        }

        return NwcPayInvoiceRequest(
            request = request,
            scope = scope,
            invalidateHandle = { invalidateHandle(handle) }
        )
    }

    private suspend fun ensureClient(connection: WalletConnection): NwcClientHandle {
        val uri = connection.uri
        cachedHandle?.takeIf { it.uri == uri }?.let { return it }
        val deferred = clientMutex.withLock {
            cachedHandle?.takeIf { it.uri == uri }?.let { return@withLock null }
            inFlightHandle?.takeIf { it.isActive }?.let { return@withLock it }

            val created = scope.async {
                clientFactory.create(connection)
            }
            inFlightHandle = created
            activeUri = uri
            created
        }
        if (deferred == null) {
            return cachedHandle ?: ensureClient(connection)
        }

        return try {
            val handle = deferred.await()
            clientMutex.withLock {
                // Verify activeUri hasn't changed while we were creating the client
                if (activeUri != uri) {
                    // Wallet switched, discard this handle to prevent caching wrong wallet
                    runCatching { handle.release() }
                    throw CancellationException("Wallet changed during client creation")
                }
                cachedHandle = handle
                inFlightHandle = null
                handle
            }
        } catch (error: Throwable) {
            clientMutex.withLock {
                if (inFlightHandle === deferred) {
                    inFlightHandle = null
                    activeUri = null
                }
            }
            throw error
        }
    }

    private suspend fun invalidateHandle(handle: NwcClientHandle) {
        clientMutex.withLock {
            if (cachedHandle === handle) {
                closeCachedHandleLocked()
            }
        }
    }

    private suspend fun closeCachedHandle() {
        clientMutex.withLock { closeCachedHandleLocked() }
    }

    private suspend fun closeCachedHandleLocked() {
        val previous = cachedHandle
        cachedHandle = null
        activeUri = null
        inFlightHandle = null
        if (previous != null) {
            runCatching { previous.release() }
        }
    }
}
