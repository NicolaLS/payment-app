package xyz.lilsus.blip.presentation.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import rayl_suite.blip.shared.generated.resources.Res
import rayl_suite.blip.shared.generated.resources.session_transactions_empty
import rayl_suite.blip.shared.generated.resources.session_transactions_title
import rayl_suite.blip.shared.generated.resources.transaction_fee
import rayl_suite.blip.shared.generated.resources.transaction_status_failure
import rayl_suite.blip.shared.generated.resources.transaction_status_pending
import rayl_suite.blip.shared.generated.resources.transaction_status_success
import xyz.lilsus.blip.domain.format.rememberAmountFormatter
import xyz.lilsus.blip.presentation.common.AppFadingLazyColumn
import xyz.lilsus.blip.presentation.common.AppListRow
import xyz.lilsus.blip.presentation.common.BackIconButton
import xyz.lilsus.blip.presentation.main.PendingStatus
import xyz.lilsus.blip.presentation.main.SessionTransactionItem
import xyz.lilsus.blip.presentation.util.formatTimeHHmm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionTransactionsScreen(
    transactions: List<SessionTransactionItem>,
    onBack: () -> Unit,
    onTransactionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(Res.string.session_transactions_title)) },
                navigationIcon = { BackIconButton(onClick = onBack) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            EmptyTransactionsContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding()
            )
        } else {
            val containerColor = MaterialTheme.colorScheme.background
            AppFadingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .navigationBarsPadding(),
                containerColor = containerColor,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions, key = { it.id }) { transaction ->
                    SessionTransactionRow(
                        transaction = transaction,
                        onClick = { onTransactionSelected(transaction.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTransactionsContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(Res.string.session_transactions_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SessionTransactionRow(transaction: SessionTransactionItem, onClick: () -> Unit) {
    val formatter = rememberAmountFormatter()
    val amount = formatter.format(transaction.amount)
    val time = remember(transaction.createdAtMs) { formatTimeHHmm(transaction.createdAtMs) }
    val status = transaction.status.presentation()
    val supportingText = when (transaction.status) {
        PendingStatus.Success -> transaction.fee?.let {
            stringResource(Res.string.transaction_fee, formatter.format(it))
        }

        PendingStatus.Failure -> transaction.errorMessage

        PendingStatus.Waiting -> null
    }

    AppListRow(onClick = onClick) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = status.containerColor,
            contentColor = status.contentColor
        ) {
            Icon(
                modifier = Modifier.padding(8.dp),
                imageVector = status.icon,
                contentDescription = null
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = amount,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = status.label,
                style = MaterialTheme.typography.bodySmall,
                color = status.contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            supportingText?.let { supporting ->
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PendingStatus.presentation(): TransactionStatusPresentation = when (this) {
    PendingStatus.Waiting -> TransactionStatusPresentation(
        label = stringResource(Res.string.transaction_status_pending),
        icon = Icons.Filled.HourglassTop,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )

    PendingStatus.Success -> TransactionStatusPresentation(
        label = stringResource(Res.string.transaction_status_success),
        icon = Icons.Filled.CheckCircle,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    )

    PendingStatus.Failure -> TransactionStatusPresentation(
        label = stringResource(Res.string.transaction_status_failure),
        icon = Icons.Filled.Error,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )
}

private data class TransactionStatusPresentation(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)
