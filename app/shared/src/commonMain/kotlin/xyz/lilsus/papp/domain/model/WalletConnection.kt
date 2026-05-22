package xyz.lilsus.papp.domain.model

/**
 * Represents a stored wallet connection credential.
 * Supports both NWC (Nostr Wallet Connect) and Blink API key connections.
 */
data class WalletConnection(
    /**
     * Stable app identifier for this wallet.
     *
     * For NWC this is the wallet pubkey. For Blink this is a generated local ID that maps
     * to API credentials in secure storage.
     */
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
    /** Optional wallet metadata snapshot. Currently populated for NWC discovery. */
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
