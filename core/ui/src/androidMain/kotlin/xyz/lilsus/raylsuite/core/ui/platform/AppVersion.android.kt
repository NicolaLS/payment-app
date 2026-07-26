package xyz.lilsus.raylsuite.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun appVersionName(): String {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    return context.packageManager
        .getPackageInfo(context.packageName, 0)
        .versionName
        ?: "unknown"
}
