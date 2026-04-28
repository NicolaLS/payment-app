package xyz.lilsus.papp

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSBundle

@OptIn(ExperimentalNativeApi::class)
actual val isDebugBuild: Boolean = Platform.isDebugBinary

actual val appStorageNamespace: String =
    NSBundle.mainBundle.bundleIdentifier ?: "xyz.lilsus.papp"

actual val allowE2eHooks: Boolean = appStorageNamespace.endsWith(".e2e")
