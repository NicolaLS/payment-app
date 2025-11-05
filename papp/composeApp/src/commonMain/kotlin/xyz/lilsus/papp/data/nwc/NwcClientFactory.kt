package xyz.lilsus.papp.data.nwc

import io.github.nostr.nwc.NwcClient
import io.github.nostr.nwc.NwcClientContract
import io.github.nostr.nwc.NwcSession
import io.github.nostr.nwc.NwcSessionManager
import io.ktor.client.*
import kotlinx.coroutines.CoroutineScope

data class NwcClientHandle(
    val uri: String,
    val session: NwcSession,
    val client: NwcClientContract,
    val release: suspend () -> Unit,
)

fun interface NwcClientFactory {
    suspend fun create(uri: String): NwcClientHandle
}

class RealNwcClientFactory(
    private val sessionManager: NwcSessionManager,
    private val scope: CoroutineScope,
    private val httpClient: HttpClient,
    private val defaultTimeoutMillis: Long = DEFAULT_NWC_TIMEOUT_MILLIS,
) : NwcClientFactory {
    override suspend fun create(uri: String): NwcClientHandle {
        val session = sessionManager.acquire(uri = uri, autoOpen = false)
        val client = NwcClient.create(
            credentials = session.credentials,
            scope = scope,
            session = session,
            ownsSession = false,
            httpClient = httpClient,
            ownsHttpClient = false,
            requestTimeoutMillis = defaultTimeoutMillis,
        )
        return NwcClientHandle(
            uri = uri,
            session = session,
            client = client,
            release = {
                runCatching { client.close() }
                sessionManager.release(session)
            },
        )
    }
}

internal const val DEFAULT_NWC_TIMEOUT_MILLIS = 30_000L
