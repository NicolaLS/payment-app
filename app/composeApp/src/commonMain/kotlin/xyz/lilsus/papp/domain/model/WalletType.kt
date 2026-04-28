package xyz.lilsus.papp.domain.model

/**
 * Identifies the type of wallet connection.
 * Used to route payment operations to the appropriate backend.
 */
enum class WalletType {
    /**
     * Nostr Wallet Connect - the standard protocol.
     */
    NWC,

    /**
     * Blink wallet connected via API key.
     * This is a temporary bridge until Blink supports NWC natively.
     */
    BLINK
}
