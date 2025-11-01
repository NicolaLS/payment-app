package xyz.lilsus.papp.domain.model

/**
 * Represents the metadata advertised by an NWC wallet prior to establishing a full connection.
 */
data class WalletDiscovery(
    val uri: String,
    val walletPublicKey: String,
    val relayUrl: String?,
    val lud16: String?,
    val aliasSuggestion: String?,
    val methods: Set<String>,
    val encryptionSchemes: Set<String>,
    val notifications: Set<String>,
    val network: String?,
    val color: String?,
)

val WalletDiscovery.supportsPayInvoice: Boolean
    get() = methods.any { it.equals("pay_invoice", ignoreCase = true) }

val WalletDiscovery.supportsNip44: Boolean
    get() = encryptionSchemes.any { it.equals("nip44_v2", ignoreCase = true) }
