package xyz.lilsus.raylsuite.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.feature.onboarding.R

@Composable
fun WalletInstructionsScreen(
    title: String,
    introduction: String,
    steps: List<AnnotatedString>,
    stepIndex: Int,
    totalSteps: Int,
    onConnectWallet: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    require(steps.isNotEmpty()) { "Wallet instructions require at least one step" }

    OnboardingScaffold(
        stepIndex = stepIndex,
        totalSteps = totalSteps,
        onBack = onBack
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .testTag(OnboardingTestTags.WALLET_INSTRUCTIONS_SCREEN)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = introduction,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            InstructionSteps(steps)

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onConnectWallet,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(OnboardingTestTags.WALLET_INSTRUCTIONS_CONTINUE)
            ) {
                Text(stringResource(R.string.onboarding_add_wallet_button))
            }
        }
    }
}

@Composable
private fun InstructionSteps(steps: List<AnnotatedString>) {
    val bodyStyle = MaterialTheme.typography.bodyMedium

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            steps.forEachIndexed { index, step ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${index + 1}.",
                        style = bodyStyle,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.size(24.dp)
                    )
                    BasicText(
                        text = step,
                        style =
                            bodyStyle.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    )
                }
            }
        }
    }
}
