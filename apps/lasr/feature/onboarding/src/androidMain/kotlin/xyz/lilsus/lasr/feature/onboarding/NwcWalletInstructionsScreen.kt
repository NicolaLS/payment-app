package xyz.lilsus.lasr.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import xyz.lilsus.lasr.feature.onboarding.R
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
        title = stringResource(R.string.onboarding_add_wallet_title),
        introduction = stringResource(R.string.onboarding_add_wallet_intro),
        steps =
            listOf(
                buildAnnotatedString {
                    append(stringResource(R.string.onboarding_add_wallet_step1))
                },
                buildAnnotatedString {
                    append(stringResource(R.string.onboarding_add_wallet_step2))
                },
                buildAnnotatedString {
                    append(stringResource(R.string.onboarding_add_wallet_step3))
                }
            ),
        stepIndex = stepIndex,
        totalSteps = totalSteps,
        onConnectWallet = onConnectWallet,
        onBack = onBack,
        modifier = modifier
    )
}
