package xyz.lilsus.papp.data.nwc

import io.github.nicolals.nwc.NwcClient
import io.github.nicolals.nwc.NwcResult
import io.github.nicolals.nwc.WalletDiscovery as NwcWalletDiscovery
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletDiscovery
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository

private const val DEFAULT_DISCOVERY_TIMEOUT_MILLIS = 10_000L

class WalletDiscoveryRepositoryImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val httpClient: HttpClient
) : WalletDiscoveryRepository {

    override suspend fun discover(uri: String): WalletDiscovery = withContext(dispatcher) {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.InvalidWalletUri())
        }

        when (
            val result = NwcClient.discover(
                trimmed,
                httpClient,
                DEFAULT_DISCOVERY_TIMEOUT_MILLIS
            )
        ) {
            is NwcResult.Success -> result.value.toDomain()
            is NwcResult.Failure -> throw result.error.toAppErrorException()
        }
    }

    private fun NwcWalletDiscovery.toDomain(): WalletDiscovery = WalletDiscovery(
        uri = uri.raw,
        walletPublicKey = uri.walletPubkey.hex,
        relayUrl = uri.relays.firstOrNull(),
        lud16 = uri.lud16,
        aliasSuggestion = details?.alias,
        methods = walletInfo.capabilityStrings,
        encryptionSchemes = walletInfo.encryptionStrings,
        negotiatedEncryption = walletInfo.preferredEncryption.tag,
        encryptionDefaultedToNip04 = walletInfo.encryptionDefaultedToNip04,
        notifications = walletInfo.notificationStrings,
        network = details?.network,
        color = details?.color
    )
}
