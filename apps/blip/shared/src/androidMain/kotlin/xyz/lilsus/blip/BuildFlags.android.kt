package xyz.lilsus.blip

import android.content.pm.ApplicationInfo
import xyz.lilsus.blip.platform.AndroidAppContext

actual val isDebugBuild: Boolean
    get() {
        val applicationInfo = AndroidAppContext.applicationOrNull?.applicationInfo ?: return false
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

actual val appStorageNamespace: String
    get() = AndroidAppContext.application.packageName
