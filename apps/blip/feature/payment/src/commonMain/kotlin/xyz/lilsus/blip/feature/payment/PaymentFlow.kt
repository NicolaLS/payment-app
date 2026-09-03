package xyz.lilsus.blip.feature.payment

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.payment.generated.resources.Res
import xyz.lilsus.blip.feature.payment.generated.resources.tap_dismiss_pending_blink
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.paymenthub.host.PaymentHubController
import xyz.lilsus.raylsuite.feature.paymenthub.lens.PaymentHubLensDefinition
import xyz.lilsus.raylsuite.feature.paymentui.PaymentFlow as SharedPaymentFlow
import xyz.lilsus.raylsuite.feature.paymentui.PaymentFlowState
import xyz.lilsus.raylsuite.feature.paymentui.PaymentIntent
import xyz.lilsus.raylsuite.feature.paymentui.PaymentSessionItem
import xyz.lilsus.raylsuite.feature.paymentui.PaymentTransactionDetail
import xyz.lilsus.raylsuite.feature.paymentui.localizedMessage

@Composable
fun PaymentFlow(
    coordinator: PaymentCoordinator,
    paymentHub: PaymentHubController,
    lens: PaymentHubLensDefinition,
    appTitle: String,
    estimatedFeeHint: String?,
    errorMessageFor: @Composable (PaymentUiError) -> String,
    eventErrorMessageFor: suspend (PaymentUiError) -> String,
    onNavigateSettings: () -> Unit,
    onNavigateLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by coordinator.uiState.collectAsState()
    val sessionTransactions by coordinator.sessionTransactions.collectAsState()
    val newSessionTransactionCount by coordinator.newSessionTransactionCount.collectAsState()
    val transactionDetailNavigationTarget by
        coordinator.transactionDetailNavigationTarget.collectAsState()
    val hubState by paymentHub.state.collectAsState()
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
                transactionDetailNavigationTarget = transactionDetailNavigationTarget
            ),
        messageEvents = eventMessages,
        appTitle = appTitle,
        estimatedFeeHint = estimatedFeeHint,
        hub = hubState,
        lens = lens,
        onIntent = dispatch,
        onHubIntent = paymentHub::dispatch,
        onNavigateSettings = onNavigateSettings,
        onNavigateLibrary = onNavigateLibrary,
        modifier = modifier
    )
}

@Composable
private fun SessionTransactionItem.toPaymentTransactionDetail(
    errorMessageFor: @Composable (PaymentUiError) -> String
) = PaymentTransactionDetail(
    id = id,
    state = toDetailUiState().toPaymentScreenState(errorMessageFor),
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
