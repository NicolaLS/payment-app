package xyz.lilsus.papp.domain.model

/**
 * Represents a stored wallet connection credential.
 * Supports both NWC (Nostr Wallet Connect) and Blink API key connections.
 */
data class WalletConnection(
    /** Unique identifier for this wallet (NWC pubkey or generated ID for Blink). */
    val walletPublicKey: String,
    /** User-provided alias for display. */
    val alias: String?,
    /** Type of wallet connection. */
    val type: WalletType = WalletType.NWC,
    // NWC-specific fields
    /** NWC connection URI (only for NWC wallets). */
    val uri: String = "",
    /** Relay URL extracted from NWC URI. */
    val relayUrl: String? = null,
    /** Lightning address from NWC info. */
    val lud16: String? = null,
    /** NWC wallet metadata snapshot. */
    val metadata: WalletMetadataSnapshot? = null
) {
    /** Returns true if this is an NWC wallet. */
    val isNwc: Boolean get() = type == WalletType.NWC

    /** Returns true if this is a Blink wallet. */
    val isBlink: Boolean get() = type == WalletType.BLINK
}

data class WalletMetadataSnapshot(
    val methods: Set<String> = emptySet(),
    val encryptionSchemes: Set<String> = emptySet(),
    val negotiatedEncryption: String? = null,
    val encryptionDefaultedToNip04: Boolean = false,
    val notifications: Set<String> = emptySet(),
    val network: String? = null,
    val color: String? = null
)
