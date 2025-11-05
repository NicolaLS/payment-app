package xyz.lilsus.papp.presentation.main.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.*
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.presentation.theme.AppTheme

/**
 * UI model for the manual amount entry sheet.
 */
data class ManualAmountUiState(
    val amount: DisplayAmount?,
    val currency: DisplayCurrency,
    val min: DisplayAmount? = null,
    val max: DisplayAmount? = null,
    val allowDecimal: Boolean = true,
)

sealed class ManualAmountKey {
    data class Digit(val value: Int) : ManualAmountKey()
    object Decimal : ManualAmountKey()
    object Backspace : ManualAmountKey()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualAmountBottomSheet(
    state: ManualAmountUiState,
    onKeyPress: (ManualAmountKey) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.enter_amount_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            AmountInputDisplay(state)

            if (state.min != null || state.max != null) {
                Spacer(modifier = Modifier.height(12.dp))
                RangeHint(state.min, state.max)
            }

            Spacer(modifier = Modifier.height(16.dp))

            AmountKeypad(
                onKeyPress = onKeyPress,
                enableDecimal = state.allowDecimal
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                enabled = state.amount?.minor?.let { it > 0 } == true,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(Res.string.pay_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AmountInputDisplay(state: ManualAmountUiState) {
    val formatter = rememberAmountFormatter()
    val amount = state.amount
    val hasInput = amount != null
    val displayText = amount?.let { formatter.format(it) } ?: "0"

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = if (hasInput) FontWeight.SemiBold else FontWeight.Normal,
                color = if (hasInput) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
            ),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RangeHint(min: DisplayAmount?, max: DisplayAmount?) {
    val formatter = rememberAmountFormatter()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        if (min != null) {
            RangePill(
                label = stringResource(
                    Res.string.enter_amount_range_min,
                    formatter.format(min)
                )
            )
        }
        if (min != null && max != null) {
            Spacer(modifier = Modifier.width(8.dp))
        }
        if (max != null) {
            RangePill(
                label = stringResource(
                    Res.string.enter_amount_range_max,
                    formatter.format(max)
                )
            )
        }
    }
}

@Composable
private fun RangePill(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun AmountKeypad(
    onKeyPress: (ManualAmountKey) -> Unit,
    enableDecimal: Boolean,
) {
    val rows = listOf(
        listOf<ManualAmountKey>(
            ManualAmountKey.Digit(1),
            ManualAmountKey.Digit(2),
            ManualAmountKey.Digit(3)
        ),
        listOf(
            ManualAmountKey.Digit(4),
            ManualAmountKey.Digit(5),
            ManualAmountKey.Digit(6)
        ),
        listOf(
            ManualAmountKey.Digit(7),
            ManualAmountKey.Digit(8),
            ManualAmountKey.Digit(9)
        ),
        listOf(
            ManualAmountKey.Decimal,
            ManualAmountKey.Digit(0),
            ManualAmountKey.Backspace
        )
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacing = 12.dp
        val columns = 3
        val rawButtonSize = (maxWidth - spacing * (columns - 1)) / columns
        val buttonSize = rawButtonSize.coerceAtMost(72.dp)

        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        spacing,
                        Alignment.CenterHorizontally
                    )
                ) {
                    row.forEach { key ->
                        val enabled = when (key) {
                            ManualAmountKey.Decimal -> enableDecimal
                            else -> true
                        }
                        AmountKeyButton(
                            key = key,
                            enabled = enabled,
                            onClick = { onKeyPress(key) },
                            size = buttonSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountKeyButton(
    key: ManualAmountKey,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp,
) {
    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
    ) {
        Text(
            text = when (key) {
                is ManualAmountKey.Digit -> key.value.toString()
                ManualAmountKey.Decimal -> "."
                ManualAmountKey.Backspace -> "<"
            },
            fontSize = 20.sp
        )
    }
}

@Preview
@Composable
private fun ManualAmountBottomSheetPreview() {
    AppTheme {
        ManualAmountBottomSheet(
            state = ManualAmountUiState(
                amount = DisplayAmount(12345, DisplayCurrency.Satoshi),
                currency = DisplayCurrency.Satoshi,
            ),
            onKeyPress = {},
            onSubmit = {},
            onDismiss = {}
        )
    }
}

@Preview
@Composable
private fun ManualAmountBottomSheetPreviewWithRange() {
    AppTheme {
        ManualAmountBottomSheet(
            state = ManualAmountUiState(
                amount = null,
                currency = DisplayCurrency.Fiat("usd"),
                min = DisplayAmount(1000, DisplayCurrency.Fiat("usd")),
                max = DisplayAmount(5000, DisplayCurrency.Fiat("usd"))
            ),
            onKeyPress = {},
            onSubmit = {},
            onDismiss = {}
        )
    }
}
