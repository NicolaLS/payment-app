package xyz.lilsus.rayl.foundation.ui.presentation.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.rayl.foundation.ui.domain.model.AppError
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayAmount
import xyz.lilsus.rayl.foundation.ui.generated.resources.Res
import xyz.lilsus.rayl.foundation.ui.generated.resources.tap_dismiss_pending
import xyz.lilsus.rayl.foundation.ui.presentation.main.MainUiState
import xyz.lilsus.rayl.foundation.ui.presentation.main.PendingStatus
import xyz.lilsus.rayl.foundation.ui.presentation.main.SessionTransactionItem
import xyz.lilsus.rayl.foundation.ui.presentation.main.components.hero.Hero

@Composable
fun SessionTransactionDetailScreen(transaction: SessionTransactionItem, onDismiss: () -> Unit) {
    val detailState = transaction.toDetailUiState()
    val receiptPreimage = (detailState as? MainUiState.Success)
        ?.preimage
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    var showReceipt by remember { mutableStateOf(false) }
    LaunchedEffect(receiptPreimage) {
        showReceipt = false
    }
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .clickable(
                    indication = null,
                    interactionSource = null,
                    onClick = onDismiss
                )
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Hero(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                uiState = detailState,
                receiptPreimage = receiptPreimage.takeIf { showReceipt }
            )
            when (detailState) {
                is MainUiState.Success,
                is MainUiState.Error
                -> ResultLayout(
                    modifier = Modifier.fillMaxSize(),
                    result = detailState,
                    receiptVisible = showReceipt,
                    onViewReceipt = { showReceipt = true }
                )

                else -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = stringResource(Res.string.tap_dismiss_pending),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun SessionTransactionItem.toDetailUiState(): MainUiState = when (status) {
    PendingStatus.Waiting -> MainUiState.Loading()

    PendingStatus.Success -> {
        val paidAmount = resultAmount ?: amount
        MainUiState.Success(
            amountPaid = paidAmount,
            feePaid = fee ?: paidAmount.zero(),
            showBlinkFeeHint = showBlinkFeeHint && !wasAlreadyPaid,
            wasAlreadyPaid = wasAlreadyPaid,
            preimage = preimage
        )
    }

    PendingStatus.Failure -> MainUiState.Error(
        error ?: AppError.Unexpected(errorMessage)
    )
}

private fun DisplayAmount.zero(): DisplayAmount = DisplayAmount(0, currency)
