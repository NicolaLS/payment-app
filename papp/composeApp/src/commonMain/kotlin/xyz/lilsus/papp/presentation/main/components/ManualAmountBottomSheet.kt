package xyz.lilsus.papp.presentation.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.enter_amount_range_max
import papp.composeapp.generated.resources.enter_amount_range_min
import papp.composeapp.generated.resources.enter_amount_title
import papp.composeapp.generated.resources.pay_button
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
    val rawWhole: String = "0",
    val rawFraction: String = "",
    val hasDecimal: Boolean = false,
    val rangeStatus: RangeStatus = RangeStatus.InRange
)

sealed interface RangeStatus {
    object InRange : RangeStatus
    object Unknown : RangeStatus
    data class BelowMin(val min: DisplayAmount) : RangeStatus
    data class AboveMax(val max: DisplayAmount) : RangeStatus
}

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
    onRangeClick: (DisplayAmount) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
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
                RangeHint(state.min, state.max, onRangeClick)
            }

            RangeFeedback(state.rangeStatus)

            Spacer(modifier = Modifier.height(16.dp))

            AmountKeypad(
                onKeyPress = onKeyPress,
                enableDecimal = state.allowDecimal
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                enabled = state.amount?.minor?.let { it > 0 } == true &&
                    state.rangeStatus == RangeStatus.InRange,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.pay_button))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AmountInputDisplay(state: ManualAmountUiState) {
    val isActive = state.amount != null ||
        state.hasDecimal ||
        state.rawFraction.isNotEmpty() ||
        state.rawWhole != "0"
    val committedColor = if (isActive) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }
    val ghostColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val amountText = buildAnnotatedString {
        append(committedNumber(state))
        if (state.allowDecimal && state.hasDecimal && state.rawFraction.isEmpty()) {
            withStyle(SpanStyle(color = ghostColor, fontWeight = FontWeight.Normal)) {
                append("0")
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = amountText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = committedColor
                ),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(10.dp))
            CurrencyTag(
                label = currencyLabel(state.currency),
                active = isActive
            )
        }
    }
}

private fun committedNumber(state: ManualAmountUiState): String {
    val whole = formatWhole(state.rawWhole)
    val shouldShowDecimal = state.allowDecimal &&
        (state.hasDecimal || state.rawFraction.isNotEmpty())
    if (!shouldShowDecimal) return whole

    return buildString {
        append(whole)
        append(".")
        append(state.rawFraction)
    }
}

private fun formatWhole(raw: String): String {
    val normalized = raw.ifEmpty { "0" }
    if (normalized.length <= 3) return normalized
    val reversed = normalized.reversed().chunked(3).joinToString(",")
    return reversed.reversed()
}

private fun currencyLabel(currency: DisplayCurrency): String = when (currency) {
    DisplayCurrency.Bitcoin -> "BTC"
    DisplayCurrency.Satoshi -> "sat"
    is DisplayCurrency.Fiat -> currency.iso4217.uppercase()
}

@Composable
private fun RangeFeedback(rangeStatus: RangeStatus) {
    val formatter = rememberAmountFormatter()
    val feedback = when (rangeStatus) {
        RangeStatus.InRange, RangeStatus.Unknown -> null

        is RangeStatus.BelowMin -> stringResource(
            Res.string.enter_amount_range_min,
            formatter.format(rangeStatus.min)
        )

        is RangeStatus.AboveMax -> stringResource(
            Res.string.enter_amount_range_max,
            formatter.format(rangeStatus.max)
        )
    } ?: return

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = feedback,
        style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun CurrencyTag(label: String, active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (active) colors.primary.copy(alpha = 0.12f) else colors.surfaceVariant,
        contentColor = if (active) colors.primary else colors.onSurfaceVariant
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun RangeHint(
    min: DisplayAmount?,
    max: DisplayAmount?,
    onRangeClick: (DisplayAmount) -> Unit
) {
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
                ),
                onClick = { onRangeClick(min) }
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
                ),
                onClick = { onRangeClick(max) }
            )
        }
    }
}

@Composable
private fun RangePill(label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun AmountKeypad(onKeyPress: (ManualAmountKey) -> Unit, enableDecimal: Boolean) {
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
private fun AmountKeyButton(key: ManualAmountKey, enabled: Boolean, onClick: () -> Unit, size: Dp) {
    ElevatedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(size)
            .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp)
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
                currency = DisplayCurrency.Satoshi
            ),
            onKeyPress = {},
            onRangeClick = {},
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
            onRangeClick = {},
            onSubmit = {},
            onDismiss = {}
        )
    }
}
