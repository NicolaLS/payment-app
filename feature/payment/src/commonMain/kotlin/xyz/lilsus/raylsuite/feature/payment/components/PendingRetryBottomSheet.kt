package xyz.lilsus.raylsuite.feature.payment.components

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
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.core.ui.platform.enableTestTagsAsResourceId
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme
import xyz.lilsus.raylsuite.feature.payment.PaymentTestTags
import xyz.lilsus.raylsuite.feature.payment.PendingStatus
import xyz.lilsus.raylsuite.feature.payment.generated.resources.Res
import xyz.lilsus.raylsuite.feature.payment.generated.resources.pending_retry_create_new_invoice
import xyz.lilsus.raylsuite.feature.payment.generated.resources.pending_retry_create_new_payment
import xyz.lilsus.raylsuite.feature.payment.generated.resources.pending_retry_dynamic_body
import xyz.lilsus.raylsuite.feature.payment.generated.resources.pending_retry_dynamic_title
import xyz.lilsus.raylsuite.feature.payment.generated.resources.pending_retry_resolved_dynamic_body
import xyz.lilsus.raylsuite.feature.payment.generated.resources.pending_retry_resolved_title
import xyz.lilsus.raylsuite.feature.payment.generated.resources.pending_retry_view_pending
import xyz.lilsus.raylsuite.feature.payment.generated.resources.pending_retry_view_resolved

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PendingRetryBottomSheet(
    status: PendingStatus,
    onCreateNewInvoice: () -> Unit,
    onViewPending: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isResolved = status != PendingStatus.Waiting

    ModalBottomSheet(
        modifier = Modifier.enableTestTagsAsResourceId(),
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PaymentTestTags.PENDING_RETRY_SHEET)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    if (isResolved) {
                        Res.string.pending_retry_resolved_title
                    } else {
                        Res.string.pending_retry_dynamic_title
                    }
                ),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    if (isResolved) {
                        Res.string.pending_retry_resolved_dynamic_body
                    } else {
                        Res.string.pending_retry_dynamic_body
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isResolved) {
                    Button(
                        onClick = onCreateNewInvoice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(PaymentTestTags.PENDING_RETRY_CREATE_NEW_INVOICE_BUTTON)
                    ) {
                        Text(stringResource(Res.string.pending_retry_create_new_payment))
                    }
                } else {
                    OutlinedButton(
                        onClick = onCreateNewInvoice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(PaymentTestTags.PENDING_RETRY_CREATE_NEW_INVOICE_BUTTON)
                    ) {
                        Text(stringResource(Res.string.pending_retry_create_new_invoice))
                    }
                }

                OutlinedButton(
                    onClick = onViewPending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PaymentTestTags.PENDING_RETRY_VIEW_PENDING_BUTTON)
                ) {
                    Text(
                        stringResource(
                            if (isResolved) {
                                Res.string.pending_retry_view_resolved
                            } else {
                                Res.string.pending_retry_view_pending
                            }
                        )
                    )
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
private fun PendingRetryBottomSheetPreview() {
    RaylSuiteTheme {
        PendingRetryBottomSheet(
            status = PendingStatus.Waiting,
            onCreateNewInvoice = {},
            onViewPending = {},
            onDismiss = {}
        )
    }
}
