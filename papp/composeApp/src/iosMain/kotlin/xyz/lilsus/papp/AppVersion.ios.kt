package xyz.lilsus.papp

import platform.Foundation.NSBundle

actual val appVersionName: String = NSBundle.mainBundle
    .infoDictionary
    ?.get("CFBundleShortVersionString") as? String ?: "?"
