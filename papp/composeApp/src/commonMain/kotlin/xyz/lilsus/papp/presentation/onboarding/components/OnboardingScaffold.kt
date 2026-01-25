package xyz.lilsus.papp.presentation.onboarding.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import xyz.lilsus.papp.domain.model.OnboardingStep

private val MAIN_STEPS = listOf(
    OnboardingStep.Welcome,
    OnboardingStep.Features,
    OnboardingStep.AutoPaySettings,
    OnboardingStep.WalletTypeChoice,
    OnboardingStep.NoWalletHelp,
    OnboardingStep.Agreement,
    OnboardingStep.AddWallet
)

@Composable
fun OnboardingScaffold(
    currentStep: OnboardingStep,
    showBackButton: Boolean = true,
    onBack: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val systemBarsPadding = WindowInsets.systemBars.asPaddingValues()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(systemBarsPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Top bar with back button and progress
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBackButton && currentStep != OnboardingStep.Welcome) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                StepIndicator(currentStep = currentStep)

                // Placeholder for symmetry
                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main content
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
private fun StepIndicator(currentStep: OnboardingStep) {
    val currentIndex = MAIN_STEPS.indexOf(currentStep).takeIf { it >= 0 } ?: 0

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MAIN_STEPS.forEachIndexed { index, _ ->
            val isActive = index <= currentIndex
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}
