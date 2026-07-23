package xyz.lilsus.rayl.blip.domain

data class ConnectionProfile(
    val id: ConnectionId,
    val alias: String,
    val accountId: BlinkAccountId,
    val walletId: BlinkWalletId,
    val status: ConnectionStatus,
    val createdAtMillis: Long
)

enum class ConnectionStatus {
    Connected,
    NeedsReauthentication,
    Disconnected
}

interface CredentialVault {
    suspend fun put(connectionId: ConnectionId, apiKey: BlinkApiKey)
    suspend fun get(connectionId: ConnectionId): BlinkApiKey?
    suspend fun remove(connectionId: ConnectionId)
}

sealed interface ConnectBlinkOutcome {
    data class Connected(val profile: ConnectionProfile) : ConnectBlinkOutcome
    data object InvalidInput : ConnectBlinkOutcome
    data object InvalidApiKey : ConnectBlinkOutcome
    data object PermissionDenied : ConnectBlinkOutcome
    data object RateLimited : ConnectBlinkOutcome
    data object NetworkUnavailable : ConnectBlinkOutcome
    data object Unexpected : ConnectBlinkOutcome
}
