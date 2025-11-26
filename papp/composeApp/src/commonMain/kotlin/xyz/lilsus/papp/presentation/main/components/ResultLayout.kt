package xyz.lilsus.papp.presentation.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.*
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.presentation.common.errorMessageFor
import xyz.lilsus.papp.presentation.main.MainUiState
import xyz.lilsus.papp.presentation.theme.AppTheme

@Composable
fun ResultLayout(result: MainUiState, modifier: Modifier = Modifier) {
    val formatter = rememberAmountFormatter()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (result) {
            is MainUiState.Success -> {
                Text(
                    text = stringResource(Res.string.result_paid_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = formatter.format(result.amountPaid),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
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
            }

            is MainUiState.Error -> {
                Text(
                    text = stringResource(Res.string.result_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .fillMaxWidth(0.9f)
                ) {
                    Text(
                        text = errorMessageFor(result.error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {}
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.tap_continue),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ResultLayoutPreviewSuccess() {
    val amountPaid = DisplayAmount(12345, DisplayCurrency.Satoshi)
    val feePaid = DisplayAmount(69, DisplayCurrency.Satoshi)
    AppTheme {
        ResultLayout(
            modifier = Modifier.fillMaxWidth(),
            result = MainUiState.Success(
                amountPaid = amountPaid,
                feePaid = feePaid
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ResultLayoutPreviewError() {
    val errorMsg = "Something went wrong"
    AppTheme {
        ResultLayout(
            modifier = Modifier.fillMaxWidth(),
            result = MainUiState.Error(AppError.Unexpected(errorMsg))
        )
    }
}
