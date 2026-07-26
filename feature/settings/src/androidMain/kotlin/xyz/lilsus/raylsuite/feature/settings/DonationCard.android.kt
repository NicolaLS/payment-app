package xyz.lilsus.raylsuite.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import xyz.lilsus.raylsuite.feature.settings.generated.resources.Res
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_donate_subtitle
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_donate_tier_large
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_donate_tier_medium
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_donate_tier_small
import xyz.lilsus.raylsuite.feature.settings.generated.resources.settings_donate_title

@Composable
actual fun DonationCard(
    appName: String,
    onDonate1k: () -> Unit,
    onDonate5k: () -> Unit,
    onDonate10k: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings_donate_title, appName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(Res.string.settings_donate_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DonationButton(
                    label = stringResource(Res.string.settings_donate_tier_small),
                    onClick = onDonate1k,
                    modifier = Modifier.weight(1f)
                )
                DonationButton(
                    label = stringResource(Res.string.settings_donate_tier_medium),
                    onClick = onDonate5k,
                    modifier = Modifier.weight(1f)
                )
                DonationButton(
                    label = stringResource(Res.string.settings_donate_tier_large),
                    onClick = onDonate10k,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DonationButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        modifier = modifier.height(48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        onClick = onClick
    ) {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false
        )
    }
}
