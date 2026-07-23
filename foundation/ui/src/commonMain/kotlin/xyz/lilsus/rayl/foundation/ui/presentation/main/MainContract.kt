package xyz.lilsus.rayl.foundation.ui.presentation.main

import xyz.lilsus.rayl.foundation.ui.domain.lnurl.LightningAddress
import xyz.lilsus.rayl.foundation.ui.domain.model.AppError
import xyz.lilsus.rayl.foundation.ui.domain.model.ContactRole
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayAmount
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ManualAmountKey
import xyz.lilsus.rayl.foundation.ui.presentation.main.contacts.PaySheetTab

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
    data class PaymentDeepLinkReceived(val rawValue: String) : MainIntent
    data class TransactionDetailNavigationHandled(val id: String) : MainIntent
    data object SessionTransactionsOpened : MainIntent
    data object DismissResult : MainIntent
    data class ManualAmountKeyPress(val key: ManualAmountKey) : MainIntent
    data class ManualAmountPreset(val amount: DisplayAmount) : MainIntent
    data object ManualAmountSubmit : MainIntent
    data object ManualAmountDismiss : MainIntent
    data object ConfirmPaymentSubmit : MainIntent
    data object ConfirmPaymentDismiss : MainIntent
    data object PendingRetryCreateNewInvoice : MainIntent
    data object PendingRetryViewPending : MainIntent
    data object PendingRetryDismiss : MainIntent
    data class StartDonation(val amountSats: Long, val address: LightningAddress) : MainIntent

    data object OpenContacts : MainIntent
    data object DismissContacts : MainIntent
    data class PaySheetTabSelected(val tab: PaySheetTab) : MainIntent
    data class ContactRoleSelected(val role: ContactRole?) : MainIntent
    data class SelectShortcut(val id: String) : MainIntent
    data class SelectContact(val id: String) : MainIntent
    data class EditContact(val id: String) : MainIntent
    data class ContactEditorAliasChanged(val alias: String) : MainIntent
    data class ContactEditorAddressChanged(val address: String) : MainIntent
    data class ContactEditorRoleSelected(val role: ContactRole?) : MainIntent
    data object ContactEditorSave : MainIntent
    data object ContactEditorDelete : MainIntent
    data object ContactEditorDismiss : MainIntent
    data class SaveContactPromptAliasChanged(val alias: String) : MainIntent
    data class SaveContactPromptRoleSelected(val role: ContactRole?) : MainIntent
    data object SaveContactPromptSave : MainIntent
    data object SaveContactPromptDismiss : MainIntent
}
