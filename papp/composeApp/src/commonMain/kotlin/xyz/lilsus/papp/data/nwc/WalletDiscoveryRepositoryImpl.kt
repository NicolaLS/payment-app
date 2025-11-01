package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcClient
import io.github.nostr.nwc.NwcEncryptionException
import io.github.nostr.nwc.NwcException
import io.github.nostr.nwc.NwcRequestException
import io.github.nostr.nwc.NwcTimeoutException
import io.github.nostr.nwc.model.EncryptionScheme
import io.github.nostr.nwc.model.Network
import io.github.nostr.nwc.model.WalletMetadata
import io.github.nostr.nwc.parseNwcUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletDiscovery
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository
import xyz.lilsus.papp.domain.util.parseWalletPublicKey

class WalletDiscoveryRepositoryImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : WalletDiscoveryRepository {

    override suspend fun discover(uri: String): WalletDiscovery = withContext(dispatcher) {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.InvalidWalletUri())
        }
        val credentials = runCatching { parseNwcUri(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }
        val walletPublicKey = runCatching { parseWalletPublicKey(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }

        var client: NwcClient? = null
        val scope = CoroutineScope(currentCoroutineContext())
        try {
            client = NwcClient.create(uri = trimmed, scope = scope)
            val metadata = fetchMetadata(client)
            val info = runCatching { client.getInfo() }.getOrNull()

            WalletDiscovery(
                uri = trimmed,
                walletPublicKey = walletPublicKey,
                relayUrl = credentials.relays.firstOrNull(),
                lud16 = credentials.lud16,
                aliasSuggestion = info?.alias,
                methods = metadata?.capabilities ?: info?.methods ?: emptySet(),
                encryptionSchemes = metadata?.encryptionSchemes
                    ?.map(EncryptionScheme::wireName)
                    ?.toSet()
                    ?: emptySet(),
                notifications = metadata?.notificationTypes ?: info?.notifications ?: emptySet(),
                network = info?.network?.takeUnless { it == Network.UNKNOWN }?.name?.lowercase(),
                color = info?.color,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (app: AppErrorException) {
            throw app
        } catch (timeout: NwcTimeoutException) {
            throw AppErrorException(AppError.Timeout, timeout)
        } catch (request: NwcRequestException) {
            val error = request.error
            throw AppErrorException(AppError.Unexpected(error.message), request)
        } catch (nwc: NwcException) {
            val appError = when {
                nwc.cause?.isNetworkIOException() == true -> AppError.NetworkUnavailable
                nwc is NwcEncryptionException -> AppError.Unexpected(nwc.message)
                else -> AppError.Unexpected(nwc.message)
            }
            throw AppErrorException(appError, nwc)
        } catch (throwable: Throwable) {
            val appError = when {
                throwable.isNetworkIOException() -> AppError.NetworkUnavailable
                else -> AppError.Unexpected(throwable.message)
            }
            throw AppErrorException(appError, throwable)
        } finally {
            client?.close()
        }
    }

    private suspend fun fetchMetadata(client: NwcClient): WalletMetadata? {
        return try {
            client.refreshWalletMetadata()
        } catch (encryption: NwcEncryptionException) {
            client.walletMetadata.value
        }
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
