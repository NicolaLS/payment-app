package xyz.lilsus.papp.presentation.onboarding.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import lasr.composeapp.generated.resources.Res
import lasr.composeapp.generated.resources.onboarding_wallet_choice_blink_description
import lasr.composeapp.generated.resources.onboarding_wallet_choice_blink_title
import lasr.composeapp.generated.resources.onboarding_wallet_choice_no_wallet
import lasr.composeapp.generated.resources.onboarding_wallet_choice_nwc_description
import lasr.composeapp.generated.resources.onboarding_wallet_choice_nwc_title
import lasr.composeapp.generated.resources.onboarding_wallet_choice_question
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.domain.model.OnboardingStep
import xyz.lilsus.papp.domain.model.WalletType
import xyz.lilsus.papp.presentation.onboarding.components.OnboardingScaffold

@Composable
fun WalletTypeChoiceScreen(
    selectedType: WalletType?,
    onSelectWalletType: (WalletType) -> Unit,
    onSelectNoWallet: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScaffold(
        currentStep = OnboardingStep.WalletTypeChoice,
        onBack = onBack
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .testTag(MaestroTags.Onboarding.WALLET_CHOICE_SCREEN),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_wallet_choice_question),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                WalletOptionCard(
                    modifier = Modifier.testTag(MaestroTags.Onboarding.WALLET_CHOICE_BLINK_OPTION),
                    title = stringResource(Res.string.onboarding_wallet_choice_blink_title),
                    description = stringResource(
                        Res.string.onboarding_wallet_choice_blink_description
                    ),
                    isSelected = selectedType == WalletType.BLINK,
                    onClick = { onSelectWalletType(WalletType.BLINK) }
                )

                WalletOptionCard(
                    modifier = Modifier.testTag(MaestroTags.Onboarding.WALLET_CHOICE_NWC_OPTION),
                    title = stringResource(Res.string.onboarding_wallet_choice_nwc_title),
                    description = stringResource(
                        Res.string.onboarding_wallet_choice_nwc_description
                    ),
                    isSelected = selectedType == WalletType.NWC,
                    onClick = { onSelectWalletType(WalletType.NWC) }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(
                onClick = onSelectNoWallet,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag(MaestroTags.Onboarding.WALLET_CHOICE_NO_WALLET_BUTTON)
            ) {
                Text(text = stringResource(Res.string.onboarding_wallet_choice_no_wallet))
            }
        }
    }
}

@Composable
private fun WalletOptionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
