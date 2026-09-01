package xyz.lilsus.raylsuite.feature.paymentui

import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.hero.RaylHeroPhase
import xyz.lilsus.raylsuite.feature.paymentui.amount.ManualAmountUiState
import xyz.lilsus.raylsuite.feature.paymentui.components.PaymentResultPresentation
import xyz.lilsus.raylsuite.feature.paymentui.contacts.PaymentContactsUiState

sealed interface PaymentScreenState {
    data object Active : PaymentScreenState

    data object Detected : PaymentScreenState

    data class Loading(val kind: PaymentLoadingKind) : PaymentScreenState

    data class EnterAmount(
        val entry: ManualAmountUiState,
        val lnurlPayDisplay: LnurlPayDisplay? = null
    ) : PaymentScreenState

    data class Confirm(
        val amount: PaymentConfirmationAmount,
        val lnurlPayDisplay: LnurlPayDisplay? = null
    ) : PaymentScreenState

    data class PendingRetry(val transactionId: String) : PaymentScreenState

    data class Success(
        val amountPaid: DisplayAmount,
        val feePaid: DisplayAmount,
        val showEstimatedFeeHint: Boolean,
        val wasAlreadyPaid: Boolean,
        val preimage: String?
    ) : PaymentScreenState

    data class Error(val message: String) : PaymentScreenState
}

enum class PaymentLoadingKind {
    Resolving,
    Paying
}

data class PaymentConfirmationAmount(
    val primary: DisplayAmount,
    val exactSats: DisplayAmount? = null,
    val primaryIsEstimate: Boolean = false
)

data class PaymentSessionTransaction(
    val id: String,
    val amount: DisplayAmount,
    val statusLabel: String,
    val statusTone: PaymentStatusTone,
    val createdAtMs: Long,
    val supportingText: String?
)

data class PaymentSessionReference(
    val id: String,
    val statusKey: String,
    val previousPaymentSituation: PreviousPaymentSituation
)

data class PaymentTransactionDetail(
    val id: String,
    val state: PaymentScreenState,
    val canRetry: Boolean = false,
    val pendingMessage: String? = null
)

data class PaymentSessionItem(
    val reference: PaymentSessionReference,
    val transaction: PaymentSessionTransaction,
    val detail: PaymentTransactionDetail
) {
    val id: String = transaction.id

    init {
        require(reference.id == id && detail.id == id) {
            "Payment session projections must share an ID"
        }
    }
}

data class PaymentFlowState(
    val payment: PaymentScreenState,
    val sessionItems: List<PaymentSessionItem>,
    val newSessionTransactionCount: Int = 0,
    val contacts: PaymentContactsUiState = PaymentContactsUiState(),
    val transactionDetailNavigationTarget: String? = null
)

enum class PaymentStatusTone {
    Pending,
    Success,
    Failure
}

fun PaymentScreenState.toHeroPhase(): RaylHeroPhase = when (this) {
    PaymentScreenState.Active -> RaylHeroPhase.Ready

    is PaymentScreenState.Detected,
    is PaymentScreenState.Confirm,
    is PaymentScreenState.EnterAmount,
    is PaymentScreenState.PendingRetry -> RaylHeroPhase.Acknowledged

    is PaymentScreenState.Loading ->
        if (kind == PaymentLoadingKind.Resolving) {
            RaylHeroPhase.Acknowledged
        } else {
            RaylHeroPhase.Processing
        }

    is PaymentScreenState.Success -> RaylHeroPhase.Succeeded

    is PaymentScreenState.Error -> RaylHeroPhase.Failed
}

fun PaymentScreenState.toResultPresentation(): PaymentResultPresentation = when (this) {
    is PaymentScreenState.Success ->
        PaymentResultPresentation.Success(
            amountPaid = amountPaid,
            feePaid = feePaid,
            showEstimatedFeeHint = showEstimatedFeeHint,
            wasAlreadyPaid = wasAlreadyPaid,
            hasReceipt = !preimage.isNullOrBlank()
        )

    is PaymentScreenState.Error -> PaymentResultPresentation.Error(message)

    else -> error("Only terminal payment states have result presentation")
}
