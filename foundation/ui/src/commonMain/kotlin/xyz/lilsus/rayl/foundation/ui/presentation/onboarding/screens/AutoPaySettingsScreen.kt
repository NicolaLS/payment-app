package xyz.lilsus.rayl.foundation.ui.presentation.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.rayl.foundation.ui.MaestroTags
import xyz.lilsus.rayl.foundation.ui.domain.format.rememberAmountFormatter
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayAmount
import xyz.lilsus.rayl.foundation.ui.domain.model.DisplayCurrency
import xyz.lilsus.rayl.foundation.ui.domain.model.OnboardingStep
import xyz.lilsus.rayl.foundation.ui.domain.model.PaymentConfirmationMode
import xyz.lilsus.rayl.foundation.ui.generated.resources.Res
import xyz.lilsus.rayl.foundation.ui.generated.resources.onboarding_autopay_always
import xyz.lilsus.rayl.foundation.ui.generated.resources.onboarding_autopay_body
import xyz.lilsus.rayl.foundation.ui.generated.resources.onboarding_autopay_continue
import xyz.lilsus.rayl.foundation.ui.generated.resources.onboarding_autopay_hint
import xyz.lilsus.rayl.foundation.ui.generated.resources.onboarding_autopay_threshold
import xyz.lilsus.rayl.foundation.ui.generated.resources.onboarding_autopay_threshold_label
import xyz.lilsus.rayl.foundation.ui.generated.resources.onboarding_autopay_title
import xyz.lilsus.rayl.foundation.ui.presentation.common.ThresholdSlider
import xyz.lilsus.rayl.foundation.ui.presentation.onboarding.components.OnboardingScaffold

@Composable
fun AutoPaySettingsScreen(
    confirmationMode: PaymentConfirmationMode,
    thresholdSats: Long,
    secondaryEquivalent: String?,
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
            modifier = modifier
                .fillMaxSize()
                .testTag(MaestroTags.Onboarding.AUTO_PAY_SCREEN),
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
                    modifier = Modifier
                        .padding(16.dp)
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Always confirm option
                    Row(
                        modifier = Modifier
                            .heightIn(48.dp)
                            .fillMaxWidth()
                            .selectable(
                                selected = confirmationMode == PaymentConfirmationMode.Always,
                                role = Role.RadioButton,
                                onClick = {
                                    onConfirmationModeChanged(PaymentConfirmationMode.Always)
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = confirmationMode == PaymentConfirmationMode.Always,
                            onClick = null
                        )
                        Text(
                            text = stringResource(Res.string.onboarding_autopay_always),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Auto-pay below threshold option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(48.dp)
                            .selectable(
                                selected = confirmationMode == PaymentConfirmationMode.Above,
                                role = Role.RadioButton,
                                onClick = {
                                    onConfirmationModeChanged(PaymentConfirmationMode.Above)
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = confirmationMode == PaymentConfirmationMode.Above,
                            onClick = null
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
                            val labelText = if (secondaryEquivalent != null) {
                                stringResource(
                                    Res.string.onboarding_autopay_threshold_label,
                                    "$satsFormatted ($secondaryEquivalent)"
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
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MaestroTags.Onboarding.AUTO_PAY_CONTINUE_BUTTON)
            ) {
                Text(text = stringResource(Res.string.onboarding_autopay_continue))
            }
        }
    }
}
