package xyz.lilsus.flint.feature.payment.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.flint.feature.payment.PaymentTestTags
import xyz.lilsus.flint.feature.payment.generated.resources.Res
import xyz.lilsus.flint.feature.payment.generated.resources.confirm_payment_title
import xyz.lilsus.flint.feature.payment.generated.resources.dismiss_button
import xyz.lilsus.flint.feature.payment.generated.resources.pay_button
import xyz.lilsus.raylsuite.core.model.DisplayAmount
import xyz.lilsus.raylsuite.core.model.DisplayCurrency
import xyz.lilsus.raylsuite.core.ui.format.rememberAmountFormatter
import xyz.lilsus.raylsuite.core.ui.platform.enableTestTagsAsResourceId
import xyz.lilsus.raylsuite.core.ui.theme.RaylSuiteTheme

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ConfirmationBottomSheet(
    confirmAmount: DisplayAmount,
    onPay: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        modifier = Modifier.enableTestTagsAsResourceId(),
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        val formatter = rememberAmountFormatter()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PaymentTestTags.CONFIRMATION_SHEET)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.confirm_payment_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = formatter.format(confirmAmount),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PaymentTestTags.CONFIRMATION_PAY_BUTTON)
                ) {
                    Text(stringResource(Res.string.pay_button))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PaymentTestTags.CONFIRMATION_DISMISS_BUTTON)
                ) {
                    Text(stringResource(Res.string.dismiss_button))
                }
            }
        }
    }
}

@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
fun ConfirmationBottomSheetPreview() {
    val confirmAmount = DisplayAmount(69, DisplayCurrency.Satoshi)
    RaylSuiteTheme {
        ConfirmationBottomSheet(
            confirmAmount = confirmAmount,
            onPay = {},
            onDismiss = {}
        )
    }
}
