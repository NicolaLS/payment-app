package xyz.lilsus.blip.feature.payment

import xyz.lilsus.raylsuite.core.model.ContactRole
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountKey
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentSheetTab

sealed interface PaymentEvent {
    data class ShowError(val error: PaymentUiError) : PaymentEvent

    data class ShowToast(val message: PaymentToastMessage) : PaymentEvent
}

sealed interface PaymentToastMessage {
    data object BitcoinAddressNotSupported : PaymentToastMessage

    data object Bolt12NotSupported : PaymentToastMessage
}

sealed interface PaymentIntent {
    data class QrCodeScanned(val rawValue: String) : PaymentIntent

    data class DeepLinkReceived(val rawValue: String) : PaymentIntent

    data class TransactionDetailNavigationHandled(val id: String) : PaymentIntent

    data object SessionTransactionsOpened : PaymentIntent

    data object DismissResult : PaymentIntent

    data class ManualAmountKeyPress(val key: ManualAmountKey) : PaymentIntent

    data class ManualAmountPreset(val amount: DisplayAmount) : PaymentIntent

    data object ManualAmountSubmit : PaymentIntent

    data object ManualAmountDismiss : PaymentIntent

    data object ConfirmPaymentSubmit : PaymentIntent

    data object ConfirmPaymentDismiss : PaymentIntent

    data object PendingRetryCreateNewInvoice : PaymentIntent

    data object PendingRetryRetryPrevious : PaymentIntent

    data object PendingRetryViewPending : PaymentIntent

    data object PendingRetryDismiss : PaymentIntent

    data class RetryTransaction(val id: String) : PaymentIntent

    data class StartDonation(val amountSats: Long, val address: LightningAddress) : PaymentIntent

    data object OpenContacts : PaymentIntent

    data object DismissContacts : PaymentIntent

    data class PaymentSheetTabSelected(val tab: PaymentSheetTab) : PaymentIntent

    data class ContactRoleSelected(val role: ContactRole?) : PaymentIntent

    data class SelectShortcut(val id: String) : PaymentIntent

    data class SelectContact(val id: String) : PaymentIntent

    data class SaveContactPromptAliasChanged(val alias: String) : PaymentIntent

    data class SaveContactPromptRoleSelected(val role: ContactRole?) : PaymentIntent

    data object SaveContactPromptSave : PaymentIntent

    data object SaveContactPromptDismiss : PaymentIntent
}
