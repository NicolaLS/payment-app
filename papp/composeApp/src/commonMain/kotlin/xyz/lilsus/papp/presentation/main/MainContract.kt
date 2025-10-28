package xyz.lilsus.papp.presentation.main

import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.presentation.main.components.ManualAmountKey

/**
 * Defines the one-off events emitted by [MainViewModel] for the UI layer to handle.
 */
sealed interface MainEvent {
    /**
     * Requests that the UI open the camera scanner to capture a BOLT11 invoice.
     */
    data object OpenScanner : MainEvent

    /**
     * Notifies the UI about an error that should be displayed out of band (snackbar, toast, etc.).
     */
    data class ShowError(val error: AppError) : MainEvent
}

/**
 * Intents that describe user-driven interactions with the main payment flow.
 */
sealed interface MainIntent {
    data object RequestScan : MainIntent
    data class InvoiceDetected(val invoice: String) : MainIntent
    data object DismissResult : MainIntent
    data class ManualAmountKeyPress(val key: ManualAmountKey) : MainIntent
    data object ManualAmountSubmit : MainIntent
    data object ManualAmountDismiss : MainIntent
}
