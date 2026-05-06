package xyz.lilsus.papp.presentation.main.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import xyz.lilsus.papp.MaestroTags

@Composable
fun SettingsFAB(onNavigateSettings: () -> Unit) {
    FloatingActionButton(
        onClick = onNavigateSettings,
        modifier = Modifier.testTag(MaestroTags.Payment.SETTINGS_BUTTON),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Icon(Icons.Filled.Settings, "Settings")
    }
}
