package xyz.lilsus.papp.domain.model

/**
 * Identifies the concrete wallet that should handle a payment operation.
 *
 * The value carried by each target is intentionally backend-specific:
 * NWC needs the connection URI, while Blink needs the app's stored wallet ID.
 */
sealed interface WalletPaymentTarget {
    val type: WalletType

    data class Nwc(val uri: String) : WalletPaymentTarget {
        override val type: WalletType = WalletType.NWC
    }

    data class Blink(val walletId: String) : WalletPaymentTarget {
        override val type: WalletType = WalletType.BLINK
    }
}

fun WalletConnection.toPaymentTarget(): WalletPaymentTarget? = when (type) {
    WalletType.NWC -> WalletPaymentTarget.Nwc(uri)
    WalletType.BLINK -> WalletPaymentTarget.Blink(walletPublicKey)
}
