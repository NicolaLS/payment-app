package xyz.lilsus.raylsuite.feature.paymenthub

import android.content.Context
import android.os.Build
import xyz.lilsus.raylsuite.core.hubapi.HubClientMetadata

internal fun createHubRemoteSession(context: Context): HubRemoteSession {
    val app = context.applicationContext
    val info = app.packageManager.getPackageInfo(app.packageName, 0)

    @Suppress("DEPRECATION")
    val build = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    return HubRemoteSession(
        HubClientMetadata(
            app = app.packageName,
            version = info.versionName ?: "unknown",
            build = build.toString(),
            platform = "android"
        )
    )
}
