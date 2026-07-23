package xyz.lilsus.rayl.foundation.ui.presentation.main

import xyz.lilsus.rayl.foundation.ui.domain.model.AppError
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayAmount
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.ManualAmountUiState

sealed class MainUiState {
    object Active : MainUiState()
    object Detected : MainUiState()
    data class Loading(val kind: LoadingKind = LoadingKind.Paying) : MainUiState()
    data class EnterAmount(val entry: ManualAmountUiState) : MainUiState()
    data class Confirm(val amount: DisplayAmount) : MainUiState()
    data class PendingRetry(val id: String) : MainUiState()
    data class Success(
        val amountPaid: DisplayAmount,
        val feePaid: DisplayAmount,
        val showBlinkFeeHint: Boolean = false,
        val wasAlreadyPaid: Boolean = false,
        val preimage: String? = null
    ) : MainUiState()

    data class Error(val error: AppError) : MainUiState()
}

enum class LoadingKind {
    Resolving,
    Paying
}

data class SessionTransactionItem(
    val id: String,
    val amount: DisplayAmount,
    val status: PendingStatus,
    /** Timestamp when payment was initiated (epoch millis) */
    val createdAtMs: Long,
    /** Amount to show on the result screen. This can differ from [amount] for already-paid invoices. */
    val resultAmount: DisplayAmount? = null,
    /** Fee paid, available when status is Success */
    val fee: DisplayAmount? = null,
    /** Error value, available when status is Failure */
    val error: AppError? = null,
    /** Error message, available when status is Failure */
    val errorMessage: String? = null,
    val showBlinkFeeHint: Boolean = false,
    val wasAlreadyPaid: Boolean = false,
    val preimage: String? = null
)

enum class PendingStatus {
    Waiting,
    Success,
    Failure
}
