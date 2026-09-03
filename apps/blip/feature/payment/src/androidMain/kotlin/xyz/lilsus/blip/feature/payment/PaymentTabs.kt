package xyz.lilsus.blip.feature.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.payment.generated.resources.Res
import xyz.lilsus.blip.feature.payment.generated.resources.tap_dismiss_pending_blink
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.paymentui.PaymentFlowState
import xyz.lilsus.raylsuite.feature.paymentui.PaymentSessionItem
import xyz.lilsus.raylsuite.feature.paymentui.PaymentTransactionDetail
import xyz.lilsus.raylsuite.feature.paymentui.localizedMessage

/** Projects Blip's payment state into the Android Compose Scan and Recent render state. */
@Composable
fun rememberPaymentFlowState(coordinator: PaymentCoordinator): PaymentFlowState {
    val uiState by coordinator.uiState.collectAsState()
    val sessionTransactions by coordinator.sessionTransactions.collectAsState()
    val newSessionTransactionCount by coordinator.newSessionTransactionCount.collectAsState()
    val transactionDetailNavigationTarget by
        coordinator.transactionDetailNavigationTarget.collectAsState()
    val formatter = rememberAmountFormatter()

    return PaymentFlowState(
        payment = uiState.toPaymentScreenState(::blipPaymentErrorMessageFor),
        sessionItems =
            sessionTransactions.map { transaction ->
                PaymentSessionItem(
                    reference = transaction.toPaymentSessionReference(),
                    transaction = transaction.toPaymentSessionTransaction(formatter),
                    detail = transaction.toPaymentTransactionDetail()
                )
            },
        newSessionTransactionCount = newSessionTransactionCount,
        transactionDetailNavigationTarget = transactionDetailNavigationTarget
    )
}

/** Localized snackbar text for Blip's payment errors and unsupported-input toasts. */
@Composable
fun rememberPaymentMessages(coordinator: PaymentCoordinator): Flow<String> = remember(coordinator) {
    coordinator.events.map { event ->
        when (event) {
            is PaymentEvent.ShowError -> getBlipPaymentErrorMessageFor(event.error)
            is PaymentEvent.ShowToast -> event.message.localizedMessage()
        }
    }
}

@Composable
private fun SessionTransactionItem.toPaymentTransactionDetail() = PaymentTransactionDetail(
    id = id,
    state = toDetailUiState().toPaymentScreenState(::blipPaymentErrorMessageFor),
    canRetry = status == PendingStatus.StatusUnknown || status == PendingStatus.Failure,
    pendingMessage =
        if (status == PendingStatus.PendingInBlink) {
            stringResource(Res.string.tap_dismiss_pending_blink)
        } else {
            null
        }
)

private fun SessionTransactionItem.toDetailUiState(): PaymentUiState = when (status) {
    PendingStatus.Sending,
    PendingStatus.PendingInBlink -> PaymentUiState.Loading()

    PendingStatus.Success,
    PendingStatus.AlreadyPaid -> {
        val paidAmount = resultAmount ?: amount
        PaymentUiState.Success(
            amountPaid = paidAmount,
            feePaid = fee ?: paidAmount.zero(),
            showEstimatedFeeHint = showEstimatedFeeHint && !wasAlreadyPaid,
            wasAlreadyPaid = status == PendingStatus.AlreadyPaid || wasAlreadyPaid,
            preimage = preimage
        )
    }

    PendingStatus.Failure,
    PendingStatus.StatusUnknown ->
        PaymentUiState.Error(
            error ?: PaymentUiError.Unexpected(errorMessage)
        )
}

private fun DisplayAmount.zero(): DisplayAmount = DisplayAmount(0, currency)
