package xyz.lilsus.papp.presentation.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.onboarding_no_wallet_body
import papp.composeapp.generated.resources.onboarding_no_wallet_button
import papp.composeapp.generated.resources.onboarding_no_wallet_start_again
import papp.composeapp.generated.resources.onboarding_no_wallet_title
import xyz.lilsus.papp.domain.model.OnboardingStep
import xyz.lilsus.papp.presentation.onboarding.components.OnboardingScaffold

@Composable
fun NoWalletHelpScreen(
    onHasWalletNow: () -> Unit,
    onStartAgain: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScaffold(
        currentStep = OnboardingStep.NoWalletHelp,
        onBack = onBack
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_no_wallet_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.onboarding_no_wallet_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onHasWalletNow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.onboarding_no_wallet_button))
            }

            OutlinedButton(
                onClick = onStartAgain,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.onboarding_no_wallet_start_again))
            }
        }
    }
}
