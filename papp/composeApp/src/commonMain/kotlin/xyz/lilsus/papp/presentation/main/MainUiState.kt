package xyz.lilsus.papp.presentation.main

import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.presentation.main.components.ManualAmountUiState

sealed class MainUiState {
    object Active : MainUiState()
    object Detected : MainUiState()
    object Loading : MainUiState()
    data class EnterAmount(val entry: ManualAmountUiState) : MainUiState()
    data class Confirm(val amount: DisplayAmount) : MainUiState()
    data class Pending(
        val info: PendingPaymentInfo,
        val status: PendingStatus,
        val isNotice: Boolean = false
    ) : MainUiState()
    data class Success(val amountPaid: DisplayAmount, val feePaid: DisplayAmount) : MainUiState()

    data class Error(val error: AppError) : MainUiState()
}

data class PendingPaymentInfo(val id: String, val amount: DisplayAmount)

data class PendingPaymentItem(val id: String, val amount: DisplayAmount, val status: PendingStatus)

enum class PendingStatus {
    Waiting,
    Success,
    Failure,
    TimedOut
}
