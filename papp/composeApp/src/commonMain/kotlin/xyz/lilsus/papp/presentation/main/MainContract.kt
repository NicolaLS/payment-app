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
