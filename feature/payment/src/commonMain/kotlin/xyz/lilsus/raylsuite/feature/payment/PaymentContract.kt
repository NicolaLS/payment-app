package xyz.lilsus.raylsuite.feature.payment

import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.LightningAddress
import xyz.lilsus.raylsuite.feature.payment.amount.ManualAmountKey

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

    data object PendingRetryViewPending : PaymentIntent

    data object PendingRetryDismiss : PaymentIntent

    data class StartDonation(val amountSats: Long, val address: LightningAddress) : PaymentIntent
}
