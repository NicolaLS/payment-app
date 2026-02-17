package xyz.lilsus.papp.presentation.main

import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.presentation.main.components.ManualAmountUiState

sealed class MainUiState {
    object Active : MainUiState()
    object Detected : MainUiState()
    data class Loading(val isWatchingPending: Boolean = false) : MainUiState()
    data class EnterAmount(val entry: ManualAmountUiState) : MainUiState()
    data class Confirm(val amount: DisplayAmount) : MainUiState()
    data class Success(
        val amountPaid: DisplayAmount,
        val feePaid: DisplayAmount,
        val showBlinkFeeHint: Boolean = false,
        val wasAlreadyPaid: Boolean = false
    ) : MainUiState()

    data class Error(val error: AppError) : MainUiState()
}

/**
 * A pending payment chip displayed in the bottom area.
 * Shows status inline - no navigation to detail screen.
 */
data class PendingPaymentItem(
    val id: String,
    val amount: DisplayAmount,
    val status: PendingStatus,
    /** Timestamp when payment was initiated (epoch millis) */
    val createdAtMs: Long,
    /** Fee paid, available when status is Success */
    val fee: DisplayAmount? = null,
    /** Error message, available when status is Failure */
    val errorMessage: String? = null
)

enum class PendingStatus {
    Waiting,
    Success,
    Failure
}
