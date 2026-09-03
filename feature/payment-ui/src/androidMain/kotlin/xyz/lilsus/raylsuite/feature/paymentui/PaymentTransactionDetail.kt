package xyz.lilsus.raylsuite.feature.paymentui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.feature.paymentui.R
import xyz.lilsus.raylsuite.feature.paymentui.components.PaymentHero
import xyz.lilsus.raylsuite.feature.paymentui.components.ResultLayout

@Composable
fun PaymentTransactionDetail(
    detail: PaymentTransactionDetail?,
    estimatedFeeHint: String?,
    onIntent: (PaymentIntent) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(detail) {
        if (detail == null) onBack()
    }
    DisposableEffect(detail?.id) {
        onDispose { onIntent(PaymentIntent.DismissResult) }
    }
    detail?.let {
        PaymentTransactionDetailScreen(
            detail = it,
            estimatedFeeHint = estimatedFeeHint,
            onRetry = {
                onIntent(PaymentIntent.RetryTransaction(it.id))
                onBack()
            },
            onDismiss = onBack
        )
    }
}

@Composable
private fun PaymentTransactionDetailScreen(
    detail: PaymentTransactionDetail,
    estimatedFeeHint: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val receiptPreimage =
        (detail.state as? PaymentScreenState.Success)
            ?.preimage
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    var showReceipt by remember { mutableStateOf(false) }
    LaunchedEffect(receiptPreimage) { showReceipt = false }
    Scaffold { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PaymentHero(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                phase = detail.state.toHeroPhase(),
                receiptPreimage = receiptPreimage.takeIf { showReceipt }
            )
            when (detail.state) {
                is PaymentScreenState.Success,
                is PaymentScreenState.Error ->
                    ResultLayout(
                        modifier = Modifier.fillMaxSize(),
                        result = detail.state.toResultPresentation(),
                        receiptVisible = showReceipt,
                        estimatedFeeHint = estimatedFeeHint,
                        onViewReceipt = { showReceipt = true },
                        onContinue = onDismiss,
                        actionLabel =
                            if (detail.canRetry) {
                                stringResource(R.string.retry_payment)
                            } else {
                                null
                            },
                        onAction = onRetry
                    )

                else ->
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(top = 24.dp, start = 24.dp, end = 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(
                                text =
                                    detail.pendingMessage
                                        ?: stringResource(R.string.tap_dismiss_pending),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
            }
        }
    }
}
