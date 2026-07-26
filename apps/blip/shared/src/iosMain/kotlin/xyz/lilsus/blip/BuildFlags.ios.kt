package xyz.lilsus.blip

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSBundle

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary

actual val appStorageNamespace: String =
    NSBundle.mainBundle.bundleIdentifier ?: "xyz.lilsus.blip"
