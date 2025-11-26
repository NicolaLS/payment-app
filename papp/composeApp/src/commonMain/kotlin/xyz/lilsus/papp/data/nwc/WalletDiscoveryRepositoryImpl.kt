package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.DEFAULT_REQUEST_TIMEOUT_MS
import io.github.nostr.nwc.NwcClient
import io.github.nostr.nwc.NwcUri
import io.github.nostr.nwc.model.Network
import io.github.nostr.nwc.model.NwcResult
import io.github.nostr.nwc.model.NwcWalletDescriptor
import io.ktor.client.*
import kotlinx.coroutines.*
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletDiscovery
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository

private const val DEFAULT_TIMEOUT_MILLIS = DEFAULT_REQUEST_TIMEOUT_MS

class WalletDiscoveryRepositoryImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val httpClient: HttpClient
) : WalletDiscoveryRepository {

    override suspend fun discover(uri: String): WalletDiscovery = withContext(dispatcher) {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.InvalidWalletUri())
        }
        val parsed = runCatching { NwcUri.parse(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }

        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val client = NwcClient.create(
            uri = parsed,
            scope = scope,
            httpClient = httpClient,
            requestTimeoutMillis = DEFAULT_TIMEOUT_MILLIS
        )

        try {
            when (val result = client.describeWallet(DEFAULT_TIMEOUT_MILLIS)) {
                is NwcResult.Success -> result.value.toDomain()
                is NwcResult.Failure -> throw result.failure.toAppErrorException()
            }
        } finally {
            client.close()
            scope.cancel()
        }
    }

    private fun NwcWalletDescriptor.toDomain(): WalletDiscovery = WalletDiscovery(
        uri = uri.toUriString(),
        walletPublicKey = uri.walletPublicKeyHex,
        relayUrl = relays.firstOrNull(),
        lud16 = lud16,
        aliasSuggestion = alias,
        methods = capabilities.map { it.wireName }.toSet(),
        encryptionSchemes = encryptionSchemes.map { it.wireName }.toSet(),
        negotiatedEncryption = negotiatedEncryption?.wireName,
        encryptionDefaultedToNip04 = metadata.encryptionDefaultedToNip04,
        notifications = notifications.map { it.wireName }.toSet(),
        network = network.takeUnless { it == Network.UNKNOWN }?.name?.lowercase(),
        color = color
    )
}
