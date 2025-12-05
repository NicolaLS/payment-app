package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcSessionManager
import io.github.nostr.nwc.NwcWallet
import io.github.nostr.nwc.NwcWalletContract
import io.github.nostr.nwc.model.EncryptionScheme
import io.github.nostr.nwc.model.NwcCapability
import io.github.nostr.nwc.model.NwcNotificationType
import io.github.nostr.nwc.model.WalletMetadata
import kotlinx.coroutines.CoroutineScope
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletMetadataSnapshot

/**
 * Factory for creating [NwcWalletContract] instances from wallet connections.
 */
fun interface NwcWalletFactory {
    /**
     * Create an [NwcWalletContract] for the given connection.
     * This returns immediately - client initialization is deferred until first request.
     */
    fun create(connection: WalletConnection): NwcWalletContract
}

/**
 * Default implementation that creates [NwcWallet] instances using [NwcSessionManager].
 */
class RealNwcWalletFactory(
    private val sessionManager: NwcSessionManager,
    private val scope: CoroutineScope,
    private val requestTimeoutMillis: Long = DEFAULT_NWC_REQUEST_TIMEOUT_MILLIS
) : NwcWalletFactory {
    override fun create(connection: WalletConnection): NwcWalletContract = NwcWallet.create(
        uri = connection.uri,
        sessionManager = sessionManager,
        scope = scope,
        requestTimeoutMillis = requestTimeoutMillis,
        cachedMetadata = connection.metadata?.toNwcMetadata(),
        cachedEncryption = connection.metadata?.toPreferredEncryption()
    )
}

internal const val DEFAULT_NWC_REQUEST_TIMEOUT_MILLIS = 8_000L

// Keep the pay window generous so late wallet replies still reach the app while the UI
// handles early "pending" states. We surface a pending notice after ~3s on the UI layer.
internal const val DEFAULT_NWC_PAY_TIMEOUT_MILLIS = 45_000L

internal fun WalletMetadataSnapshot.toNwcMetadata(): WalletMetadata = WalletMetadata(
    capabilities = NwcCapability.parseAll(methods),
    encryptionSchemes = encryptionSchemes.mapNotNull { EncryptionScheme.fromWire(it) }.toSet(),
    notificationTypes = NwcNotificationType.parseAll(notifications),
    encryptionDefaultedToNip04 = encryptionDefaultedToNip04
)

internal fun WalletMetadataSnapshot.toPreferredEncryption(): EncryptionScheme? {
    val negotiated = negotiatedEncryption?.let { EncryptionScheme.fromWire(it) }
    if (negotiated != null && negotiated !is EncryptionScheme.Unknown) return negotiated
    if (encryptionSchemes.any { it.equals("nip44_v2", ignoreCase = true) }) {
        return EncryptionScheme.Nip44V2
    }
    if (encryptionDefaultedToNip04 ||
        encryptionSchemes.any { it.equals("nip04", ignoreCase = true) }
    ) {
        return EncryptionScheme.Nip04
    }
    return null
}
