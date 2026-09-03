package xyz.lilsus.lasr.feature.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.paymentui.PaymentFlowState
import xyz.lilsus.raylsuite.feature.paymentui.PaymentSessionItem
import xyz.lilsus.raylsuite.feature.paymentui.PaymentTransactionDetail
import xyz.lilsus.raylsuite.feature.paymentui.localizedMessage

/** Projects Lasr's payment state into the shared render state used by the Scan and Recent tabs. */
@Composable
fun rememberPaymentFlowState(coordinator: PaymentCoordinator): PaymentFlowState {
    val uiState by coordinator.uiState.collectAsState()
    val sessionTransactions by coordinator.sessionTransactions.collectAsState()
    val newSessionTransactionCount by coordinator.newSessionTransactionCount.collectAsState()
    val transactionDetailNavigationTarget by
        coordinator.transactionDetailNavigationTarget.collectAsState()
    val formatter = rememberAmountFormatter()

    return PaymentFlowState(
        payment = uiState.toPaymentScreenState(::lasrPaymentErrorMessageFor),
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

/** Localized snackbar text for Lasr's payment errors and unsupported-input toasts. */
@Composable
fun rememberPaymentMessages(coordinator: PaymentCoordinator): Flow<String> {
    val context = LocalContext.current
    return remember(coordinator, context) {
        coordinator.events.map { event ->
            when (event) {
                is PaymentEvent.ShowError -> getLasrPaymentErrorMessageFor(event.error, context)
                is PaymentEvent.ShowToast -> event.message.localizedMessage(context)
            }
        }
    }
}

@Composable
private fun SessionTransactionItem.toPaymentTransactionDetail() = PaymentTransactionDetail(
    id = id,
    state = toDetailUiState().toPaymentScreenState(::lasrPaymentErrorMessageFor),
    canRetry = status == PendingStatus.OutcomeUnknown || status == PendingStatus.Failed
)

private fun SessionTransactionItem.toDetailUiState(): PaymentUiState = when (status) {
    PendingStatus.Sending,
    PendingStatus.Resolving -> PaymentUiState.Loading()

    PendingStatus.Succeeded -> {
        val paidAmount = resultAmount ?: amount
        PaymentUiState.Success(
            amountPaid = paidAmount,
            feePaid = fee ?: paidAmount.zero(),
            showEstimatedFeeHint = showEstimatedFeeHint && !wasAlreadyPaid,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimage
        )
    }

    PendingStatus.OutcomeUnknown,
    PendingStatus.Failed ->
        PaymentUiState.Error(
            error ?: PaymentUiError.Unexpected(errorMessage)
        )
}

private fun DisplayAmount.zero(): DisplayAmount = DisplayAmount(0, currency)
