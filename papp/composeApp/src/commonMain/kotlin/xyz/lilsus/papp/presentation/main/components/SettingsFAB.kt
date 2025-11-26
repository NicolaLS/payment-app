package xyz.lilsus.papp.presentation.main.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SettingsFAB(onNavigateSettings: () -> Unit) {
    FloatingActionButton(
        onClick = onNavigateSettings,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.tertiary,
        elevation = FloatingActionButtonDefaults.elevation(0.dp)
    ) {
        Icon(Icons.Filled.Settings, "Settings")
    }
}
