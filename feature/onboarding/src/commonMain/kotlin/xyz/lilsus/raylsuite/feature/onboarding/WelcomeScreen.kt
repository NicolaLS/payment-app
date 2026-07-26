package xyz.lilsus.raylsuite.feature.onboarding

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.Res
import xyz.lilsus.raylsuite.feature.onboarding.generated.resources.onboarding_welcome_get_started

@Composable
fun WelcomeScreen(
    title: String,
    subtitle: String,
    description: String,
    stepIndex: Int,
    totalSteps: Int,
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScaffold(
        stepIndex = stepIndex,
        totalSteps = totalSteps,
        showBackButton = false
    ) {
        Column(
            modifier =
            modifier
                .fillMaxSize()
                .testTag(OnboardingTestTags.WELCOME_SCREEN),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onGetStarted,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(OnboardingTestTags.WELCOME_CONTINUE)
            ) {
                Text(stringResource(Res.string.onboarding_welcome_get_started))
            }
        }
    }
}
