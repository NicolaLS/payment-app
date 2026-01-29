package xyz.lilsus.papp.presentation.main

import xyz.lilsus.papp.domain.lnurl.LightningAddress
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey

/**
 * Defines the one-off events emitted by [MainViewModel] for the UI layer to handle.
 */
sealed interface MainEvent {
    /**
     * Notifies the UI about an error that should be displayed out of band (snackbar, toast, etc.).
     */
    data class ShowError(val error: AppError) : MainEvent

    /**
     * Shows a brief toast message that auto-dismisses. Used for non-blocking feedback
     * like unsupported QR code formats.
     */
    data class ShowToast(val message: ToastMessage) : MainEvent

    /**
     * Navigates to the wallet connection screen with a pre-filled NWC URI.
     * Triggered when user scans a wallet QR on the payment screen.
     */
    data class NavigateToConnectWallet(val uri: String) : MainEvent
}

/**
 * Predefined toast messages for type-safe localization.
 */
sealed interface ToastMessage {
    /** Scanned QR is a Bitcoin on-chain address, not Lightning. */
    data object BitcoinAddressNotSupported : ToastMessage

    /** Scanned QR is a BOLT12 offer which is not yet supported. */
    data object Bolt12NotSupported : ToastMessage
}

/**
 * Intents that describe user-driven interactions with the main payment flow.
 */
sealed interface MainIntent {
    data class QrCodeScanned(val rawValue: String) : MainIntent
    data object DismissResult : MainIntent
    data class ManualAmountKeyPress(val key: ManualAmountKey) : MainIntent
    data class ManualAmountPreset(val amount: DisplayAmount) : MainIntent
    data object ManualAmountSubmit : MainIntent
    data object ManualAmountDismiss : MainIntent
    data object ConfirmPaymentSubmit : MainIntent
    data object ConfirmPaymentDismiss : MainIntent
    data class StartDonation(val amountSats: Long, val address: LightningAddress) : MainIntent

    /** Tap a pending payment chip - shows result if ready, does nothing if still waiting */
    data class TapPending(val id: String) : MainIntent

    /** Swipe to switch to next wallet (swipe left). */
    data object SwipeWalletNext : MainIntent

    /** Swipe to switch to previous wallet (swipe right). */
    data object SwipeWalletPrevious : MainIntent
}

/**
 * Represents a wallet for display in the wallet indicator.
 */
data class WalletInfo(val pubKey: String, val displayName: String, val isActive: Boolean)
