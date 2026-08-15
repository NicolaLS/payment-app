package xyz.lilsus.raylsuite.feature.paymentintent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.platform.enableTestTagsAsResourceId
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.Res
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.completed_body
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.completed_title
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.create_additional_payment
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.in_progress_body
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.in_progress_title
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.outcome_unknown_body
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.outcome_unknown_title
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.retry_previous_invoice
import xyz.lilsus.raylsuite.feature.paymentintent.generated.resources.view_previous_payment

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RepeatPaymentClarificationBottomSheet(
    clarification: RepeatPaymentClarification,
    onDecision: (RepeatPaymentDecision) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val copy = clarification.situation.copy()

    ModalBottomSheet(
        modifier = Modifier.enableTestTagsAsResourceId(),
        sheetState = sheetState,
        onDismissRequest = { onDecision(RepeatPaymentDecision.Dismiss) }
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(PaymentIntentTestTags.CLARIFICATION_SHEET)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(copy.title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(copy.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (clarification.canRetryPreviousInvoice) {
                    ClarificationButton(
                        text = stringResource(Res.string.retry_previous_invoice),
                        testTag = PaymentIntentTestTags.RETRY_PREVIOUS_INVOICE_BUTTON,
                        onClick = {
                            onDecision(RepeatPaymentDecision.RetryPreviousInvoice)
                        }
                    )
                } else {
                    ClarificationButton(
                        text = stringResource(Res.string.view_previous_payment),
                        testTag = PaymentIntentTestTags.VIEW_PREVIOUS_PAYMENT_BUTTON,
                        onClick = {
                            onDecision(RepeatPaymentDecision.ViewPreviousPayment)
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        onDecision(RepeatPaymentDecision.CreateAdditionalPayment)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(
                                PaymentIntentTestTags.CREATE_ADDITIONAL_PAYMENT_BUTTON
                            )
                ) {
                    Text(stringResource(Res.string.create_additional_payment))
                }

                if (clarification.canRetryPreviousInvoice) {
                    OutlinedButton(
                        onClick = {
                            onDecision(RepeatPaymentDecision.ViewPreviousPayment)
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .testTag(
                                    PaymentIntentTestTags.VIEW_PREVIOUS_PAYMENT_BUTTON
                                )
                    ) {
                        Text(stringResource(Res.string.view_previous_payment))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClarificationButton(text: String, testTag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Text(text)
    }
}

private data class ClarificationCopy(val title: StringResource, val body: StringResource)

private fun PreviousPaymentSituation.copy(): ClarificationCopy = when (this) {
    PreviousPaymentSituation.InProgress ->
        ClarificationCopy(
            title = Res.string.in_progress_title,
            body = Res.string.in_progress_body
        )

    PreviousPaymentSituation.OutcomeUnknown ->
        ClarificationCopy(
            title = Res.string.outcome_unknown_title,
            body = Res.string.outcome_unknown_body
        )

    PreviousPaymentSituation.Completed ->
        ClarificationCopy(
            title = Res.string.completed_title,
            body = Res.string.completed_body
        )
}

@Preview(showBackground = true)
@Composable
private fun RepeatPaymentClarificationBottomSheetPreview() {
    RaylSuiteTheme {
        RepeatPaymentClarificationBottomSheet(
            clarification =
                RepeatPaymentClarification(
                    situation = PreviousPaymentSituation.OutcomeUnknown
                ),
            onDecision = {}
        )
    }
}
