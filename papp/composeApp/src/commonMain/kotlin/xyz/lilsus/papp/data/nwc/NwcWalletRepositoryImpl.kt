package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcClient
import io.github.nostr.nwc.NwcException
import io.github.nostr.nwc.NwcRequestException
import io.github.nostr.nwc.NwcTimeoutException
import io.github.nostr.nwc.model.PayInvoiceParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
 * The repository lazily instantiates and caches an [NwcClient] bound to the
 * current wallet-connect URI provided by [connectUriProvider].
 */
class NwcWalletRepositoryImpl(
    private val walletSettingsRepository: WalletSettingsRepository,
    private val scope: CoroutineScope,
    private val requestTimeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : NwcWalletRepository {

    private val clientMutex = Mutex()
    private var cachedClient: NwcClient? = null
    private var cachedUri: String? = null

    override suspend fun payInvoice(invoice: String): PaidInvoice {
        require(invoice.isNotBlank()) { "Invoice must not be blank." }
        val uri = walletSettingsRepository.getWalletConnection()?.uri?.trim()
            ?: throw AppErrorException(AppError.MissingWalletConnection)
        return try {
            val client = ensureClient(uri)
            val response = client.payInvoice(
                params = PayInvoiceParams(invoice = invoice),
                timeoutMillis = requestTimeoutMillis,
            )
            PaidInvoice(
                preimage = response.preimage,
                feesPaidMsats = response.feesPaid?.msats,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (app: AppErrorException) {
            throw app
        } catch (request: NwcRequestException) {
            val error = request.error
            throw AppErrorException(
                AppError.PaymentRejected(code = error.code, message = error.message),
                cause = request,
            )
        } catch (timeout: NwcTimeoutException) {
            throw AppErrorException(AppError.Timeout, timeout)
        } catch (nwc: NwcException) {
            val appError = when {
                nwc.cause?.isNetworkIOException() == true -> AppError.NetworkUnavailable
                else -> AppError.Unexpected(nwc.message)
            }
            throw AppErrorException(appError, nwc)
        } catch (throwable: Throwable) {
            val appError = when {
                throwable.isNetworkIOException() -> AppError.NetworkUnavailable
                else -> AppError.Unexpected(throwable.message)
            }
            throw AppErrorException(appError, throwable)
        }
    }

    private suspend fun ensureClient(uri: String): NwcClient {
        cachedClient?.takeIf { cachedUri == uri }?.let { return it }
        return clientMutex.withLock {
            cachedClient?.takeIf { cachedUri == uri }?.let { return it }

            val previous = cachedClient
            if (previous != null) {
                try {
                    previous.close()
                } catch (_: Throwable) {
                    // Ignore cleanup issues; we'll establish a fresh connection below.
                }
            }

            val created = NwcClient.create(
                uri = uri,
                scope = scope,
            )
            cachedClient = created
            cachedUri = uri
            created
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MILLIS = 30_000L
    }
}

private fun Throwable.isNetworkIOException(): Boolean {
    val name = this::class.qualifiedName
    if (name == "io.ktor.utils.io.errors.IOException") {
        return true
    }
    val cause = this.cause
    return cause != null && cause !== this && cause.isNetworkIOException()
}
