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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.onboarding_agreement_body
import papp.composeapp.generated.resources.onboarding_agreement_checkbox
import papp.composeapp.generated.resources.onboarding_agreement_continue
import papp.composeapp.generated.resources.onboarding_agreement_title
import xyz.lilsus.papp.domain.model.OnboardingStep
import xyz.lilsus.papp.presentation.onboarding.components.OnboardingScaffold

@Composable
fun AgreementScreen(
    hasAgreed: Boolean,
    onAgreementChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScaffold(
        currentStep = OnboardingStep.Agreement,
        onBack = onBack
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_agreement_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_agreement_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = hasAgreed,
                    onCheckedChange = onAgreementChanged
                )
                Text(
                    text = stringResource(Res.string.onboarding_agreement_checkbox),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onContinue,
                enabled = hasAgreed,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.onboarding_agreement_continue))
            }
        }
    }
}
