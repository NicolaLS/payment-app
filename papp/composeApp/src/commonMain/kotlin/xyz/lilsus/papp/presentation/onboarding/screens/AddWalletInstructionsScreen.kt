package xyz.lilsus.papp.presentation.onboarding.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import papp.composeapp.generated.resources.Res
import papp.composeapp.generated.resources.onboarding_add_wallet_blink_intro
import papp.composeapp.generated.resources.onboarding_add_wallet_blink_step1_prefix
import papp.composeapp.generated.resources.onboarding_add_wallet_blink_step1_suffix
import papp.composeapp.generated.resources.onboarding_add_wallet_blink_step2
import papp.composeapp.generated.resources.onboarding_add_wallet_blink_step3
import papp.composeapp.generated.resources.onboarding_add_wallet_blink_step4_prefix
import papp.composeapp.generated.resources.onboarding_add_wallet_blink_step4_suffix
import papp.composeapp.generated.resources.onboarding_add_wallet_blink_title
import papp.composeapp.generated.resources.onboarding_add_wallet_button
import papp.composeapp.generated.resources.onboarding_add_wallet_skip
import papp.composeapp.generated.resources.onboarding_add_wallet_nwc_intro
import papp.composeapp.generated.resources.onboarding_add_wallet_nwc_step1
import papp.composeapp.generated.resources.onboarding_add_wallet_nwc_step2
import papp.composeapp.generated.resources.onboarding_add_wallet_nwc_step3
import papp.composeapp.generated.resources.onboarding_add_wallet_nwc_title
import xyz.lilsus.papp.domain.model.OnboardingStep
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.presentation.onboarding.components.OnboardingScaffold

@Composable
fun AddWalletInstructionsScreen(
    walletType: WalletType,
    onConnectWallet: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showSkipButton: Boolean = false,
    onSkip: () -> Unit = {}
) {
    OnboardingScaffold(
        currentStep = OnboardingStep.AddWallet,
        onBack = onBack
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (walletType) {
                WalletType.BLINK -> BlinkInstructions()
                WalletType.NWC -> NwcInstructions()
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onConnectWallet,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(Res.string.onboarding_add_wallet_button))
            }

            if (showSkipButton) {
                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(Res.string.onboarding_add_wallet_skip))
                }
            }
        }
    }
}

private const val BLINK_DASHBOARD_URL = "https://dashboard.blink.sv"

@Composable
private fun BlinkInstructions() {
    val uriHandler = LocalUriHandler.current

    Text(
        text = stringResource(Res.string.onboarding_add_wallet_blink_title),
        style = MaterialTheme.typography.headlineSmall
    )

    Text(
        text = stringResource(Res.string.onboarding_add_wallet_blink_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))

    BlinkInstructionSteps(onOpenDashboard = { uriHandler.openUri(BLINK_DASHBOARD_URL) })
}

@Composable
private fun BlinkInstructionSteps(onOpenDashboard: () -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val bodyMediumStyle = MaterialTheme.typography.bodyMedium

    // Localized string parts
    val step1Prefix = stringResource(Res.string.onboarding_add_wallet_blink_step1_prefix)
    val step1Suffix = stringResource(Res.string.onboarding_add_wallet_blink_step1_suffix)
    val step4Prefix = stringResource(Res.string.onboarding_add_wallet_blink_step4_prefix)
    val step4Suffix = stringResource(Res.string.onboarding_add_wallet_blink_step4_suffix)

    // Step 1: "[prefix] dashboard.blink.sv [suffix]"
    val step1Text = buildAnnotatedString {
        withStyle(SpanStyle(color = onSurfaceVariant)) {
            append(step1Prefix)
        }
        pushStringAnnotation(tag = "URL", annotation = BLINK_DASHBOARD_URL)
        withStyle(
            SpanStyle(
                color = primaryColor,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append("dashboard.blink.sv")
        }
        pop()
        withStyle(SpanStyle(color = onSurfaceVariant)) {
            append(step1Suffix)
        }
    }

    // Step 4: "[prefix] blink_ [suffix]"
    val step4Text = buildAnnotatedString {
        withStyle(SpanStyle(color = onSurfaceVariant)) {
            append(step4Prefix)
        }
        withStyle(
            SpanStyle(
                color = primaryColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        ) {
            append("blink_")
        }
        withStyle(SpanStyle(color = onSurfaceVariant)) {
            append(step4Suffix)
        }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Step 1 with clickable link
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "1.",
                    style = bodyMediumStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.size(24.dp)
                )
                ClickableText(
                    text = step1Text,
                    style = bodyMediumStyle,
                    onClick = { offset ->
                        step1Text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                            .firstOrNull()?.let { onOpenDashboard() }
                    }
                )
            }

            // Step 2
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "2.",
                    style = bodyMediumStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(Res.string.onboarding_add_wallet_blink_step2),
                    style = bodyMediumStyle,
                    color = onSurfaceVariant
                )
            }

            // Step 3
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "3.",
                    style = bodyMediumStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(Res.string.onboarding_add_wallet_blink_step3),
                    style = bodyMediumStyle,
                    color = onSurfaceVariant
                )
            }

            // Step 4 with styled blink_
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "4.",
                    style = bodyMediumStyle,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = step4Text,
                    style = bodyMediumStyle
                )
            }
        }
    }
}

@Composable
private fun NwcInstructions() {
    Text(
        text = stringResource(Res.string.onboarding_add_wallet_nwc_title),
        style = MaterialTheme.typography.headlineSmall
    )

    Text(
        text = stringResource(Res.string.onboarding_add_wallet_nwc_intro),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))

    InstructionSteps(
        steps = listOf(
            stringResource(Res.string.onboarding_add_wallet_nwc_step1),
            stringResource(Res.string.onboarding_add_wallet_nwc_step2),
            stringResource(Res.string.onboarding_add_wallet_nwc_step3)
        )
    )
}

@Composable
private fun InstructionSteps(steps: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
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
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
