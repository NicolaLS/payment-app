package xyz.lilsus.papp.presentation.main.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.pending_chip_failure
import papp.composeapp.generated.resources.pending_chip_success
import papp.composeapp.generated.resources.pending_chip_waiting
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.presentation.main.PendingPaymentItem
import xyz.lilsus.papp.presentation.main.PendingStatus
import xyz.lilsus.papp.presentation.util.formatTimeHHmm

@Composable
fun BottomLayout(
    modifier: Modifier = Modifier.fillMaxWidth(),
    title: String,
    subtitle: String? = null,
    pendingPayments: List<PendingPaymentItem> = emptyList(),
    onPendingTap: (String) -> Unit = {}
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
        AnimatedVisibility(
            visible = pendingPayments.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pendingPayments.forEach { pending ->
                    PendingChip(
                        item = pending,
                        formatter = formatter,
                        onTap = { onPendingTap(pending.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingChip(
    item: PendingPaymentItem,
    formatter: xyz.lilsus.papp.domain.format.AmountFormatter,
    onTap: () -> Unit
) {
    val (containerColor, contentColor, icon) = when (item.status) {
        PendingStatus.Success -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Check
        )

        PendingStatus.Failure -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            Icons.Default.Close
        )

        PendingStatus.Waiting -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            null
        )
    }

    Surface(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onTap),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor
                )
            }
            Text(
                text = chipLabel(item, formatter),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun chipLabel(
    item: PendingPaymentItem,
    formatter: xyz.lilsus.papp.domain.format.AmountFormatter
): String {
    val amount = formatter.format(item.amount)
    val time = remember(item.createdAtMs) { formatTimeHHmm(item.createdAtMs) }
    return when (item.status) {
        PendingStatus.Waiting -> stringResource(Res.string.pending_chip_waiting, amount, time)
        PendingStatus.Success -> stringResource(Res.string.pending_chip_success, amount, time)
        PendingStatus.Failure -> stringResource(Res.string.pending_chip_failure, time)
    }
}
