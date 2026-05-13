package xyz.lilsus.papp

import android.content.pm.ApplicationInfo
import xyz.lilsus.papp.platform.AndroidAppContext

actual val isDebugBuild: Boolean
    get() {
        val applicationInfo = AndroidAppContext.applicationOrNull?.applicationInfo ?: return false
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

actual val appStorageNamespace: String
    get() = AndroidAppContext.application.packageName
