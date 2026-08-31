package xyz.lilsus.flint.feature.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.map
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.paymentui.PaymentFlow as SharedPaymentFlow
import xyz.lilsus.raylsuite.feature.paymentui.PaymentFlowState
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentui.PaymentSessionItem
import xyz.lilsus.raylsuite.feature.paymentui.PaymentTransactionDetail
import xyz.lilsus.raylsuite.feature.paymentui.localizedMessage

@Composable
fun PaymentFlow(
    coordinator: PaymentCoordinator,
    appTitle: String,
    estimatedFeeHint: String?,
    errorMessageFor: @Composable (PaymentUiError) -> String,
    eventErrorMessageFor: suspend (PaymentUiError) -> String,
    onNavigateSettings: () -> Unit,
    onNavigateShortcutCreate: () -> Unit,
    onNavigateContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by coordinator.uiState.collectAsState()
    val sessionTransactions by coordinator.sessionTransactions.collectAsState()
    val newSessionTransactionCount by coordinator.newSessionTransactionCount.collectAsState()
    val transactionDetailNavigationTarget by
        coordinator.transactionDetailNavigationTarget.collectAsState()
    val contactsState by coordinator.contactsState.collectAsState()
    val formatter = rememberAmountFormatter()
    val eventMessages = remember(coordinator, eventErrorMessageFor) {
        coordinator.events.map { event ->
            when (event) {
                is PaymentEvent.ShowError -> eventErrorMessageFor(event.error)
                is PaymentEvent.ShowToast -> event.message.localizedMessage()
            }
        }
    }
    val dispatch =
        remember(coordinator) { { intent: PaymentIntent -> coordinator.dispatch(intent) } }

    SharedPaymentFlow(
        state =
            PaymentFlowState(
                payment = uiState.toPaymentScreenState(errorMessageFor),
                sessionItems = sessionTransactions.map { transaction ->
                    PaymentSessionItem(
                        reference = transaction.toPaymentSessionReference(),
                        transaction = transaction.toPaymentSessionTransaction(formatter),
                        detail = transaction.toPaymentTransactionDetail(errorMessageFor)
                    )
                },
                newSessionTransactionCount = newSessionTransactionCount,
                contacts = contactsState,
                transactionDetailNavigationTarget = transactionDetailNavigationTarget
            ),
        messageEvents = eventMessages,
        appTitle = appTitle,
        estimatedFeeHint = estimatedFeeHint,
        onIntent = dispatch,
        onNavigateSettings = onNavigateSettings,
        onNavigateShortcutCreate = onNavigateShortcutCreate,
        onNavigateContacts = onNavigateContacts,
        modifier = modifier
    )
}

@Composable
private fun SessionTransactionItem.toPaymentTransactionDetail(
    errorMessageFor: @Composable (PaymentUiError) -> String
) = PaymentTransactionDetail(
    id = id,
    state = toDetailUiState().toPaymentScreenState(errorMessageFor),
    canRetry = status == PendingStatus.OutcomeUnknown
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
