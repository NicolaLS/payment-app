package xyz.lilsus.papp

import platform.Foundation.NSBundle

actual val isDebugBuild: Boolean =
    NSBundle.mainBundle.objectForInfoDictionaryKey("IS_DEBUG") as? Boolean ?: true
