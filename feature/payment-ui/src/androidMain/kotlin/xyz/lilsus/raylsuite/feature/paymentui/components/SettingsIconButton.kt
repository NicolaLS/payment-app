package xyz.lilsus.raylsuite.feature.paymentui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xyz.lilsus.raylsuite.feature.paymentui.PaymentTestTags
import xyz.lilsus.raylsuite.feature.paymentui.R

@Composable
fun SettingsIconButton(onNavigateSettings: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp
    ) {
        IconButton(
            onClick = onNavigateSettings,
            modifier = Modifier
                .fillMaxSize()
                .testTag(PaymentTestTags.SETTINGS_BUTTON)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings_title)
            )
        }
    }
}
