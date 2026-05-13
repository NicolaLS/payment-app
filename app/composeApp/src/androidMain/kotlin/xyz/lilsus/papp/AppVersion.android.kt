package xyz.lilsus.papp

import xyz.lilsus.papp.platform.AndroidAppContext

actual val appVersionName: String
    get() {
        val context = AndroidAppContext.application
        @Suppress("DEPRECATION")
        return context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
            ?: "unknown"
    }
