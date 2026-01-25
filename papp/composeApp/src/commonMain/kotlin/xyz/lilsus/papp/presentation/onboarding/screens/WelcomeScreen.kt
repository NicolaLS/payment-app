package xyz.lilsus.papp.presentation.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.onboarding_welcome_get_started
import papp.composeapp.generated.resources.onboarding_welcome_subtitle_line1
import papp.composeapp.generated.resources.onboarding_welcome_subtitle_line2
import papp.composeapp.generated.resources.onboarding_welcome_title
import xyz.lilsus.papp.domain.model.OnboardingStep
import xyz.lilsus.papp.presentation.onboarding.components.OnboardingScaffold

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingScaffold(
        currentStep = OnboardingStep.Welcome,
        showBackButton = false
    ) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(Res.string.onboarding_welcome_subtitle_line1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(Res.string.onboarding_welcome_subtitle_line2),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.onboarding_welcome_get_started))
            }
        }
    }
}
