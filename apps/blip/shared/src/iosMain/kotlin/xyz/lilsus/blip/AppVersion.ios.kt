package xyz.lilsus.blip

import platform.Foundation.NSBundle

actual val appVersionName: String = NSBundle.mainBundle
    .infoDictionary
    ?.get("CFBundleShortVersionString") as? String ?: "?"
