package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.model.BitcoinAmount
import io.github.nostr.nwc.model.NwcResult
import io.github.nostr.nwc.model.PayInvoiceParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.PaidInvoice
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
    private val requestTimeoutMillis: Long = DEFAULT_NWC_TIMEOUT_MILLIS,
) : NwcWalletRepository {

    private val clientMutex = Mutex()
    private var cachedHandle: NwcClientHandle? = null

    override suspend fun payInvoice(
        invoice: String,
        amountMsats: Long?,
    ): PaidInvoice {
        require(invoice.isNotBlank()) { "Invoice must not be blank." }
        if (amountMsats != null) {
            require(amountMsats > 0) { "Amount must be greater than zero." }
        }

        val connection = walletSettingsRepository.getWalletConnection()
            ?: throw AppErrorException(AppError.MissingWalletConnection)

        val handle = ensureClient(connection.uri.trim())
        val result = try {
            handle.client.payInvoice(
                params = PayInvoiceParams(
                    invoice = invoice,
                    amount = amountMsats?.let(BitcoinAmount::fromMsats),
                ),
                timeoutMillis = requestTimeoutMillis,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        }

        return when (result) {
            is NwcResult.Success -> PaidInvoice(
                preimage = result.value.preimage,
                feesPaidMsats = result.value.feesPaid?.msats,
            )

            is NwcResult.Failure -> throw result.failure.toAppErrorException()
        }
    }

    private suspend fun ensureClient(uri: String): NwcClientHandle {
        cachedHandle?.takeIf { it.uri == uri }?.let { return it }
        return clientMutex.withLock {
            cachedHandle?.takeIf { it.uri == uri }?.let { return@withLock it }

            val previous = cachedHandle
            cachedHandle = null
            if (previous != null) {
                runCatching { previous.release() }
            }

            val created = clientFactory.create(uri)
            cachedHandle = created
            created
        }
    }
}
