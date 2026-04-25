package xyz.lilsus.papp.presentation.main.components

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.pending_retry_bolt11_body
import papp.composeapp.generated.resources.pending_retry_bolt11_title
import papp.composeapp.generated.resources.pending_retry_create_new_invoice
import papp.composeapp.generated.resources.pending_retry_dynamic_body
import papp.composeapp.generated.resources.pending_retry_dynamic_title
import papp.composeapp.generated.resources.pending_retry_same_invoice
import papp.composeapp.generated.resources.pending_retry_view_pending
import xyz.lilsus.papp.presentation.main.PendingRetrySource
import xyz.lilsus.papp.presentation.theme.AppTheme

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PendingRetryBottomSheet(
    source: PendingRetrySource,
    onRetrySameInvoice: () -> Unit,
    onCreateNewInvoice: () -> Unit,
    onViewPending: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDynamic = source == PendingRetrySource.Dynamic

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(
                    if (isDynamic) {
                        Res.string.pending_retry_dynamic_title
                    } else {
                        Res.string.pending_retry_bolt11_title
                    }
                ),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    if (isDynamic) {
                        Res.string.pending_retry_dynamic_body
                    } else {
                        Res.string.pending_retry_bolt11_body
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
                Button(
                    onClick = onRetrySameInvoice,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.pending_retry_same_invoice))
                }

                if (isDynamic) {
                    OutlinedButton(
                        onClick = onCreateNewInvoice,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(Res.string.pending_retry_create_new_invoice))
                    }
                }

                OutlinedButton(
                    onClick = onViewPending,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.pending_retry_view_pending))
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
private fun PendingRetryBottomSheetPreview() {
    AppTheme {
        PendingRetryBottomSheet(
            source = PendingRetrySource.Dynamic,
            onRetrySameInvoice = {},
            onCreateNewInvoice = {},
            onViewPending = {},
            onDismiss = {}
        )
    }
}
