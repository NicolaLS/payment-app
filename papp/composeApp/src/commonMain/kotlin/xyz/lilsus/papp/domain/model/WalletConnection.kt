package xyz.lilsus.papp.domain.model

/**
 * Minimal representation of a stored Nostr Wallet Connect credential.
 */
data class WalletConnection(
    val uri: String,
    val walletPublicKey: String,
    val relayUrl: String?,
    val lud16: String?,
    val alias: String?,
)
