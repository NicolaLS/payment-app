package xyz.lilsus.raylsuite.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.Res
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_agreement_checkbox
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_agreement_continue
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_agreement_title

@Composable
fun AgreementScreen(
    body: String,
    hasAgreed: Boolean,
    stepIndex: Int,
    totalSteps: Int,
    onAgreementChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScaffold(
        stepIndex = stepIndex,
        totalSteps = totalSteps,
        onBack = onBack
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .testTag(OnboardingTestTags.AGREEMENT_SCREEN),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_agreement_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(48.dp)
                        .toggleable(
                            value = hasAgreed,
                            role = Role.Checkbox,
                            onValueChange = onAgreementChanged
                        )
                        .testTag(OnboardingTestTags.AGREEMENT_CHECKBOX),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = hasAgreed,
                    onCheckedChange = null
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(OnboardingTestTags.AGREEMENT_CONTINUE)
            ) {
                Text(stringResource(Res.string.onboarding_agreement_continue))
            }
        }
    }
}
