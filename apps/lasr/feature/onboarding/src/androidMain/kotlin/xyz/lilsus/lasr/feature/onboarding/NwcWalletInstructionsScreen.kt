package xyz.lilsus.lasr.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.lasr.feature.onboarding.generated.resources.Res
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_intro
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_step1
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_step2
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_step3
import xyz.lilsus.lasr.feature.onboarding.generated.resources.onboarding_add_wallet_title
import xyz.lilsus.raylsuite.feature.onboarding.WalletInstructionsScreen

@Composable
fun NwcWalletInstructionsScreen(
    stepIndex: Int,
    totalSteps: Int,
    onConnectWallet: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    WalletInstructionsScreen(
        title = stringResource(Res.string.onboarding_add_wallet_title),
        introduction = stringResource(Res.string.onboarding_add_wallet_intro),
        steps =
            listOf(
                buildAnnotatedString {
                    append(stringResource(Res.string.onboarding_add_wallet_step1))
                },
                buildAnnotatedString {
                    append(stringResource(Res.string.onboarding_add_wallet_step2))
                },
                buildAnnotatedString {
                    append(stringResource(Res.string.onboarding_add_wallet_step3))
                }
            ),
        stepIndex = stepIndex,
        totalSteps = totalSteps,
        onConnectWallet = onConnectWallet,
        onBack = onBack,
        modifier = modifier
    )
}
