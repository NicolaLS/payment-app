package xyz.lilsus.papp.presentation.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import lasr.shared.generated.resources.Res
import lasr.shared.generated.resources.active_wallet_chip_description
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.papp.MaestroTags
import xyz.lilsus.papp.presentation.main.WalletInfo

@Composable
fun BottomLayout(
    modifier: Modifier = Modifier.fillMaxWidth(),
    title: String,
    subtitle: String? = null,
    wallets: List<WalletInfo> = emptyList(),
    onWalletClick: () -> Unit = {}
) {
    Column(
        modifier = modifier.testTag(MaestroTags.Payment.ACTIVE_CONTENT),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            modifier = Modifier.padding(top = 16.dp),
            text = title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium
        )
        subtitle?.let {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        if (wallets.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            ActiveWalletChip(
                wallets = wallets,
                onClick = onWalletClick
            )
        }
    }
}

@Composable
private fun ActiveWalletChip(wallets: List<WalletInfo>, onClick: () -> Unit) {
    val active = wallets.find { it.isActive } ?: return
    val description = stringResource(
        Res.string.active_wallet_chip_description,
        active.displayName
    )

    AssistChip(
        modifier = Modifier
            .testTag(MaestroTags.Payment.ACTIVE_WALLET_NAME)
            .semantics { contentDescription = description },
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.AccountBalanceWallet,
                contentDescription = null
            )
        },
        label = {
            AnimatedContent(
                targetState = active.displayName,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    )
}
