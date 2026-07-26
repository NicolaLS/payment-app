package xyz.lilsus.blip.feature.onboarding

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.blip.feature.onboarding.generated.resources.Res
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_intro
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step1_prefix
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step1_suffix
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step2
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step3
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step4_prefix
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_step4_suffix
import xyz.lilsus.blip.feature.onboarding.generated.resources.onboarding_add_wallet_title
import xyz.lilsus.raylsuite.feature.onboarding.WalletInstructionsScreen

@Composable
fun BlinkWalletInstructionsScreen(
    stepIndex: Int,
    totalSteps: Int,
    onConnectWallet: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val linkStyle =
        SpanStyle(
            color = primaryColor,
            textDecoration = TextDecoration.Underline
        )

    val dashboardStep =
        buildAnnotatedString {
            withStyle(SpanStyle(color = onSurfaceVariant)) {
                append(stringResource(Res.string.onboarding_add_wallet_step1_prefix))
            }
            withLink(
                LinkAnnotation.Clickable("blink-dashboard") {
                    uriHandler.openUri(BLINK_DASHBOARD_URL)
                }
            ) {
                withStyle(linkStyle) {
                    append(BLINK_DASHBOARD_HOST)
                }
            }
            withStyle(SpanStyle(color = onSurfaceVariant)) {
                append(stringResource(Res.string.onboarding_add_wallet_step1_suffix))
            }
        }
    val apiKeyStep =
        buildAnnotatedString {
            withStyle(SpanStyle(color = onSurfaceVariant)) {
                append(stringResource(Res.string.onboarding_add_wallet_step4_prefix))
            }
            withStyle(
                SpanStyle(
                    color = primaryColor,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
            ) {
                append(BLINK_API_KEY_PREFIX)
            }
            withStyle(SpanStyle(color = onSurfaceVariant)) {
                append(stringResource(Res.string.onboarding_add_wallet_step4_suffix))
            }
        }

    WalletInstructionsScreen(
        title = stringResource(Res.string.onboarding_add_wallet_title),
        introduction = stringResource(Res.string.onboarding_add_wallet_intro),
        steps =
            listOf(
                dashboardStep,
                buildAnnotatedString {
                    append(stringResource(Res.string.onboarding_add_wallet_step2))
                },
                buildAnnotatedString {
                    append(stringResource(Res.string.onboarding_add_wallet_step3))
                },
                apiKeyStep
            ),
        stepIndex = stepIndex,
        totalSteps = totalSteps,
        onConnectWallet = onConnectWallet,
        onBack = onBack,
        modifier = modifier
    )
}

private const val BLINK_DASHBOARD_HOST = "dashboard.blink.sv"
private const val BLINK_DASHBOARD_URL = "https://$BLINK_DASHBOARD_HOST"
private const val BLINK_API_KEY_PREFIX = "blink_"
