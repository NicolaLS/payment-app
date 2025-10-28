package xyz.lilsus.papp.presentation.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.tap_continue
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.AppError
import xyz.lilsus.papp.presentation.common.errorMessageFor
import xyz.lilsus.papp.presentation.main.MainUiState
import xyz.lilsus.papp.presentation.theme.AppTheme


@Composable
fun ResultLayout(
    result: MainUiState,
    modifier: Modifier = Modifier,
) {
    val formatter = rememberAmountFormatter()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (result) {
            is MainUiState.Success -> {
                Text(
                    text = "Paid",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Fee: ${formatter.format(result.feePaid)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            is MainUiState.Error -> {
                Text(
                    text = "Error",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = errorMessageFor(result.error),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
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
    val feePaid = DisplayAmount(69, DisplayCurrency.Satoshi)
    AppTheme {
        ResultLayout(
            modifier = Modifier.fillMaxWidth(),
            result = MainUiState.Success(feePaid)
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
