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
    val metadata: WalletMetadataSnapshot? = null,
)

data class WalletMetadataSnapshot(
    val methods: Set<String> = emptySet(),
    val encryptionSchemes: Set<String> = emptySet(),
    val notifications: Set<String> = emptySet(),
    val network: String? = null,
    val color: String? = null,
)
