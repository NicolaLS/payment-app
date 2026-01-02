package xyz.lilsus.papp.data.nwc

import io.github.nicolals.nostr.nip47.model.NwcEncryption
import io.github.nicolals.nwc.NwcCapability
import io.github.nicolals.nwc.NwcClient
import io.github.nicolals.nwc.NwcConnectionUri
import io.github.nicolals.nwc.NwcNotificationType
import io.github.nicolals.nwc.WalletInfo
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletMetadataSnapshot

/**
 * Factory for creating [NwcClient] instances from wallet connections.
 */
fun interface NwcClientFactory {
    /**
     * Create an [NwcClient] for the given connection.
     * The client is not connected yet - call connect() and awaitReady() before use.
     *
     * @throws IllegalArgumentException if the connection URI is invalid.
     */
    fun create(connection: WalletConnection): NwcClient
}

/**
 * Default implementation that creates [NwcClient] instances.
 */
class RealNwcClientFactory(private val httpClient: HttpClient, private val scope: CoroutineScope) :
    NwcClientFactory {
    override fun create(connection: WalletConnection): NwcClient {
        val uri = NwcConnectionUri.parse(connection.uri)
            ?: throw IllegalArgumentException("Invalid NWC URI: ${connection.uri}")

        val cachedWalletInfo = connection.metadata?.toWalletInfo()

        return NwcClient(uri, scope, httpClient, cachedWalletInfo)
    }
}

internal const val DEFAULT_NWC_REQUEST_TIMEOUT_MILLIS = 6_000L

// Keep the pay window generous so late wallet replies still reach the app while the UI
// handles early "pending" states. We surface a pending notice after ~6s on the UI layer.
internal const val DEFAULT_NWC_PAY_TIMEOUT_MILLIS = 20_000L

// Timeout for lookup requests (verifying pending payments)
internal const val LOOKUP_TIMEOUT_MILLIS = 10_000L

/**
 * Convert persisted metadata snapshot to WalletInfo for client initialization.
 */
internal fun WalletMetadataSnapshot.toWalletInfo(): WalletInfo {
    val capabilities = methods.mapNotNull { NwcCapability.fromValue(it) }.toSet()
    val notifications = this.notifications.mapNotNull { NwcNotificationType.fromValue(it) }.toSet()
    val encryptions = encryptionSchemes.mapNotNull { NwcEncryption.fromTag(it) }.toSet()

    // Determine preferred encryption
    val preferredEncryption = when {
        negotiatedEncryption != null -> {
            NwcEncryption.fromTag(negotiatedEncryption) ?: NwcEncryption.NIP04
        }

        NwcEncryption.NIP44_V2 in encryptions -> NwcEncryption.NIP44_V2

        NwcEncryption.NIP04 in encryptions -> NwcEncryption.NIP04

        else -> NwcEncryption.NIP04
    }

    // Use effective encryptions (default to NIP04 if none specified)
    val effectiveEncryptions = encryptions.ifEmpty { setOf(NwcEncryption.NIP04) }

    return WalletInfo(
        capabilities = capabilities,
        notifications = notifications,
        encryptions = effectiveEncryptions,
        preferredEncryption = preferredEncryption,
        encryptionDefaultedToNip04 = encryptionDefaultedToNip04
    )
}
