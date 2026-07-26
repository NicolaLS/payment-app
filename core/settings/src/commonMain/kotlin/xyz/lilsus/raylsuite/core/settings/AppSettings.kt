package xyz.lilsus.raylsuite.core.settings

import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings

@Composable
expect fun rememberAppSettings(storageName: String): Settings
