package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcUri
import io.github.nostr.nwc.discoverWallet
import io.github.nostr.nwc.model.Network
import io.github.nostr.nwc.model.NwcResult
import io.github.nostr.nwc.model.NwcWalletDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.AppErrorException
import xyz.lilsus.papp.domain.model.WalletDiscovery
import xyz.lilsus.papp.domain.repository.WalletDiscoveryRepository

class WalletDiscoveryRepositoryImpl(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : WalletDiscoveryRepository {

    override suspend fun discover(uri: String): WalletDiscovery = withContext(dispatcher) {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) {
            throw AppErrorException(AppError.InvalidWalletUri())
        }
        val parsed = runCatching { NwcUri.parse(trimmed) }.getOrElse { error ->
            throw AppErrorException(AppError.InvalidWalletUri(error.message), error)
        }

        val result = try {
            discoverWallet(parsed.toUriString())
        } catch (cancellation: CancellationException) {
            throw cancellation
        }

        when (result) {
            is NwcResult.Success -> result.value.toDomain()
            is NwcResult.Failure -> throw result.failure.toAppErrorException()
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
        color = color,
    )
}
