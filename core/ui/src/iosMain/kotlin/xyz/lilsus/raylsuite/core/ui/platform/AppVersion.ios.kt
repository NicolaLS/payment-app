package xyz.lilsus.raylsuite.core.ui.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

@Composable
actual fun appVersionName(): String =
    NSBundle.mainBundle
        .infoDictionary
        ?.get("CFBundleShortVersionString") as? String
        ?: "?"
