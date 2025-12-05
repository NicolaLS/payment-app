package xyz.lilsus.papp.presentation.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.pending_status_failure
import papp.composeapp.generated.resources.pending_status_success
import papp.composeapp.generated.resources.pending_status_timeout
import papp.composeapp.generated.resources.pending_status_waiting
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.presentation.main.PendingPaymentItem
import xyz.lilsus.papp.presentation.main.PendingStatus

@Composable
fun BottomLayout(
    modifier: Modifier = Modifier.fillMaxWidth(),
    title: String,
    subtitle: String? = null,
    pendingPayments: List<PendingPaymentItem> = emptyList(),
    onPendingClick: (String) -> Unit = {}
) {
    val formatter = rememberAmountFormatter()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium
        )
        subtitle?.let {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (pendingPayments.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pendingPayments.forEach { pending ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clickable { onPendingClick(pending.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatter.format(pending.amount),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pendingStatusLabel(pending.status),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusDot(status = pending.status)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: PendingStatus) {
    val color = when (status) {
        PendingStatus.Success -> MaterialTheme.colorScheme.primary
        PendingStatus.Failure, PendingStatus.TimedOut -> MaterialTheme.colorScheme.error
        PendingStatus.Waiting -> MaterialTheme.colorScheme.outline
    }
    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(10.dp)
    ) {
        drawCircle(color = color)
    }
}

@Composable
private fun pendingStatusLabel(status: PendingStatus): String = when (status) {
    PendingStatus.Waiting -> stringResource(Res.string.pending_status_waiting)
    PendingStatus.Success -> stringResource(Res.string.pending_status_success)
    PendingStatus.Failure -> stringResource(Res.string.pending_status_failure)
    PendingStatus.TimedOut -> stringResource(Res.string.pending_status_timeout)
}
