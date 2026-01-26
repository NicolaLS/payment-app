package xyz.lilsus.papp.presentation.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.onboarding_autopay_always
import papp.composeapp.generated.resources.onboarding_autopay_body
import papp.composeapp.generated.resources.onboarding_autopay_continue
import papp.composeapp.generated.resources.onboarding_autopay_hint
import papp.composeapp.generated.resources.onboarding_autopay_threshold
import papp.composeapp.generated.resources.onboarding_autopay_threshold_label
import papp.composeapp.generated.resources.onboarding_autopay_title
import xyz.lilsus.papp.domain.format.rememberAmountFormatter
import xyz.lilsus.papp.domain.model.DisplayAmount
import xyz.lilsus.papp.domain.model.DisplayCurrency
import xyz.lilsus.papp.domain.model.OnboardingStep
import xyz.lilsus.papp.domain.model.PaymentConfirmationMode
import xyz.lilsus.papp.presentation.common.ThresholdSlider
import xyz.lilsus.papp.presentation.onboarding.components.OnboardingScaffold

@Composable
fun AutoPaySettingsScreen(
    confirmationMode: PaymentConfirmationMode,
    thresholdSats: Long,
    fiatEquivalent: String?,
    onConfirmationModeChanged: (PaymentConfirmationMode) -> Unit,
    onThresholdChanged: (Long) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = rememberAmountFormatter()

    OnboardingScaffold(
        currentStep = OnboardingStep.AutoPaySettings,
        onBack = onBack
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_autopay_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = stringResource(Res.string.onboarding_autopay_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Always confirm option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = confirmationMode == PaymentConfirmationMode.Always,
                            onClick = { onConfirmationModeChanged(PaymentConfirmationMode.Always) }
                        )
                        Text(
                            text = stringResource(Res.string.onboarding_autopay_always),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Auto-pay below threshold option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = confirmationMode == PaymentConfirmationMode.Above,
                            onClick = { onConfirmationModeChanged(PaymentConfirmationMode.Above) }
                        )
                        Text(
                            text = stringResource(Res.string.onboarding_autopay_threshold),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Threshold slider (only visible when Above mode is selected)
                    if (confirmationMode == PaymentConfirmationMode.Above) {
                        Column(
                            modifier = Modifier.padding(start = 48.dp)
                        ) {
                            val displayAmount = DisplayAmount(
                                thresholdSats,
                                DisplayCurrency.Satoshi
                            )
                            val satsFormatted = formatter.format(displayAmount)
                            val labelText = if (fiatEquivalent != null) {
                                stringResource(
                                    Res.string.onboarding_autopay_threshold_label,
                                    "$satsFormatted ($fiatEquivalent)"
                                )
                            } else {
                                stringResource(
                                    Res.string.onboarding_autopay_threshold_label,
                                    satsFormatted
                                )
                            }
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            ThresholdSlider(
                                thresholdSats = thresholdSats,
                                onThresholdChanged = onThresholdChanged
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(Res.string.onboarding_autopay_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.onboarding_autopay_continue))
            }
        }
    }
}
