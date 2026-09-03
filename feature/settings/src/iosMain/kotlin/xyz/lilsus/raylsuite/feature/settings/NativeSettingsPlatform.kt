package xyz.lilsus.raylsuite.feature.settings

import platform.Foundation.NSBundle

/** The host app version used by the native iOS Settings footer. */
fun nativeSettingsAppVersionName(): String = NSBundle.mainBundle
    .infoDictionary
    ?.get("CFBundleShortVersionString") as? String
    ?: "?"
