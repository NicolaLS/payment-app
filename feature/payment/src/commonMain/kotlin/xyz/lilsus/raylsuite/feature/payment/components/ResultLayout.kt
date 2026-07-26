package xyz.lilsus.raylsuite.feature.payment.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.feature.payment.PaymentTestTags
import xyz.lilsus.raylsuite.feature.payment.PaymentUiError
import xyz.lilsus.raylsuite.feature.payment.PaymentUiState
import xyz.lilsus.raylsuite.feature.payment.generated.resources.Res
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_already_paid_message
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_already_paid_title
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_error_title
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_paid_fee
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_paid_title
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_receipt_body_middle
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_receipt_body_only
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_receipt_body_prefix
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_receipt_body_preimage
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_receipt_body_suffix
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_receipt_title
import xyz.lilsus.raylsuite.feature.payment.generated.resources.result_view_receipt
import xyz.lilsus.raylsuite.feature.payment.generated.resources.tap_continue

@Composable
fun ResultLayout(
    result: PaymentUiState,
    errorMessageFor: @Composable (PaymentUiError) -> String,
    modifier: Modifier = Modifier,
    receiptVisible: Boolean = false,
    estimatedFeeHint: String? = null,
    onViewReceipt: () -> Unit = {}
) {
    val formatter = rememberAmountFormatter()
    Column(
        modifier = modifier.testTag(PaymentTestTags.RESULT),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (result) {
            is PaymentUiState.Success -> {
                val hasReceipt = result.preimage?.isNotBlank() == true
                if (receiptVisible && hasReceipt) {
                    PaymentReceiptText()
                } else {
                    if (result.wasAlreadyPaid) {
                        Text(
                            modifier = Modifier.testTag(PaymentTestTags.RESULT_ALREADY_PAID),
                            text = stringResource(Res.string.result_already_paid_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(Res.string.result_already_paid_message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val paidTitle = stringResource(Res.string.result_paid_title)
                        val amountPaid = formatter.format(result.amountPaid)
                        val paidSummary = buildAnnotatedString {
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) {
                                append(paidTitle)
                            }
                            append(" ")
                            append(amountPaid)
                        }
                        Text(
                            modifier = Modifier.testTag(PaymentTestTags.RESULT_SUCCESS),
                            text = paidSummary,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(
                                Res.string.result_paid_fee,
                                formatter.format(result.feePaid)
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (result.showEstimatedFeeHint && estimatedFeeHint != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = estimatedFeeHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (hasReceipt) {
                        Spacer(modifier = Modifier.height(12.dp))
                        ViewReceiptChip(onClick = onViewReceipt)
                    }
                }
            }

            is PaymentUiState.Error -> {
                Text(
                    modifier = Modifier.testTag(PaymentTestTags.RESULT_ERROR),
                    text = stringResource(Res.string.result_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                val shape = MaterialTheme.shapes.large
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            shape
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .fillMaxWidth(0.9f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessageFor(result.error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            else -> Unit
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.tap_continue),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun PaymentReceiptText() {
    val receiptBody = buildAnnotatedString {
        append(stringResource(Res.string.result_receipt_body_prefix))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(stringResource(Res.string.result_receipt_body_preimage))
        }
        append(stringResource(Res.string.result_receipt_body_middle))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(stringResource(Res.string.result_receipt_body_only))
        }
        append(stringResource(Res.string.result_receipt_body_suffix))
    }

    Text(
        text = stringResource(Res.string.result_receipt_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.tertiary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        modifier = Modifier.padding(horizontal = 24.dp),
        text = receiptBody,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ViewReceiptChip(onClick: () -> Unit) {
    AssistChip(
        modifier = Modifier.testTag(PaymentTestTags.RESULT_VIEW_RECEIPT),
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Receipt,
                contentDescription = null
            )
        },
        label = {
            Text(
                text = stringResource(Res.string.result_view_receipt),
                style = MaterialTheme.typography.labelMedium
            )
        }
    )
}
