package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcClient
import io.github.nostr.nwc.NwcClientContract
import io.github.nostr.nwc.NwcSession
import io.github.nostr.nwc.NwcSessionManager
import io.github.nostr.nwc.model.EncryptionScheme
import io.github.nostr.nwc.model.NwcCapability
import io.github.nostr.nwc.model.NwcNotificationType
import io.github.nostr.nwc.model.WalletMetadata
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import xyz.lilsus.papp.domain.model.WalletConnection
import xyz.lilsus.papp.domain.model.WalletMetadataSnapshot

data class NwcClientHandle(
    val uri: String,
    val session: NwcSession,
    val client: NwcClientContract,
    val release: suspend () -> Unit
)

fun interface NwcClientFactory {
    suspend fun create(connection: WalletConnection): NwcClientHandle
}

class RealNwcClientFactory(
    private val sessionManager: NwcSessionManager,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val handshakeTimeoutMillis: Long = DEFAULT_NWC_HANDSHAKE_TIMEOUT_MILLIS
) : NwcClientFactory {
    override suspend fun create(connection: WalletConnection): NwcClientHandle {
        val uri = connection.uri
        val session = sessionManager.acquire(uri = uri, autoOpen = false)
        val client = NwcClient.create(
            credentials = session.credentials,
            scope = scope,
            session = session,
            ownsSession = false,
            httpClient = httpClient,
            ownsHttpClient = false,
            requestTimeoutMillis = handshakeTimeoutMillis,
            cachedMetadata = connection.metadata?.toNwcMetadata(),
            cachedEncryption = connection.metadata?.toPreferredEncryption()
        )
        return NwcClientHandle(
            uri = uri,
            session = session,
            client = client,
            release = {
                runCatching { client.close() }
                sessionManager.release(session)
            }
        )
    }
}

internal const val DEFAULT_NWC_HANDSHAKE_TIMEOUT_MILLIS = 8_000L
internal const val DEFAULT_NWC_PAY_TIMEOUT_MILLIS = 10_000L

private fun WalletMetadataSnapshot.toNwcMetadata(): WalletMetadata = WalletMetadata(
    capabilities = NwcCapability.parseAll(methods),
    encryptionSchemes = encryptionSchemes.mapNotNull { EncryptionScheme.fromWire(it) }.toSet(),
    notificationTypes = NwcNotificationType.parseAll(notifications),
    encryptionDefaultedToNip04 = encryptionDefaultedToNip04
)

private fun WalletMetadataSnapshot.toPreferredEncryption(): EncryptionScheme? {
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
